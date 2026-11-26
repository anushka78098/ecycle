package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.engine.HashChain;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * QR chain of custody. Every passport (product or recovered component, already identified by a QR code) gets an append-only, per-asset SHA-256 hash chain:
 * each entry is timestamped on the server, names the acting user and role, records who holds the asset afterwards, and commits to the previous entry's hash.
 * The existing passport event stream is mirrored into the chain (see PassportService), so the Digital Product Passport remains the single source of lifecycle events.
 */
@Service @RequiredArgsConstructor
public class CustodyService {
    public static final Set<String> MANUAL_TYPES = Set.of("HANDOVER", "RECEIVED", "INSPECTED", "STORED", "TRANSPORT_STARTED", "TRANSPORT_ENDED", "NOTE");
    private static final Pattern CODE = Pattern.compile("(EC[PC]-[A-HJ-NP-Z2-9]{10})");
    private final CustodyEventRepository repo; private final PassportRepository passports; private final PassportEventRepository passportEvents;
    private final UserRepository users; private final BuyerProfileRepository buyers; private final PickupRepository pickups; private final Lookup lookup; private final NotificationService notifications;

    // ------------------------------------------------------------------ writing
    /** Appends one entry. Locks the passport row so concurrent writers serialize and sequence numbers stay gap-free. */
    @Transactional
    public CustodyEvent append(String assetCode, String type, Long actorId, String actorRole, Long custodianAfter, String location, String detail) {
        Passport p = passports.lockByCode(assetCode).orElseThrow(() -> ApiException.notFound("Asset not found: " + assetCode));
        CustodyEvent last = repo.findFirstByAssetCodeOrderBySeqDesc(assetCode).orElse(null);
        if (last == null && !"CREATED".equals(type)) {
            long n = passportEvents.findByPassportIdOrderByIdAsc(p.getId()).size();
            last = write(assetCode, null, "CHAIN_INITIALIZED", null, "SYSTEM", p.getCurrentOwnerId(), null,
                    "Chain started from the passport history (" + n + " earlier events are not individually hash-chained)");
        }
        return write(assetCode, last, type, actorId, actorRole, custodianAfter != null ? custodianAfter : (last == null ? null : last.getCustodianId()), location, detail);
    }
    private CustodyEvent write(String code, CustodyEvent prev, String type, Long actorId, String role, Long custodian, String location, String detail) {
        CustodyEvent e = new CustodyEvent();
        e.setAssetCode(code); e.setSeq(prev == null ? 1 : prev.getSeq() + 1); e.setEventType(cut(type, 60)); e.setActorId(actorId); e.setActorRole(role); e.setCustodianId(custodian);
        e.setLocation(cut(location, 200)); e.setDetail(cut(detail, 500));
        e.setOccurredAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        e.setPrevHash(prev == null ? HashChain.GENESIS : prev.getHash());
        e.setHash(HashChain.hashOf(toEntry(e, null)));
        return repo.save(e);
    }
    private static String cut(String s, int max) { return s == null ? null : (s.length() > max ? s.substring(0, max) : s); }
    private static HashChain.Entry toEntry(CustodyEvent e, String hash) {
        return new HashChain.Entry(e.getAssetCode(), e.getSeq(), e.getEventType(), e.getActorId(), e.getActorRole(), e.getCustodianId(), e.getLocation(), e.getDetail(),
                e.getOccurredAt().toEpochMilli(), e.getPrevHash(), hash == null ? e.getHash() : hash);
    }

    /** Mirrors a Digital Product Passport event into the chain; called by PassportService. Ownership changes also move custody. */
    @Transactional
    public void mirrorPassportEvent(Passport p, String type, Long actorId, String detail) {
        Long custodian = switch (type) { case "CREATED", "OWNERSHIP_TRANSFERRED", "COMPONENT_RESOLD" -> p.getCurrentOwnerId(); default -> null; };
        append(p.getPassportCode(), type, actorId, roleLabel(actorId), custodian, null, detail);
    }
    /** Custody-affecting step of a pickup (picked up / at facility ...), recorded by PickupService. */
    @Transactional
    public CustodyEvent recordSystem(String assetCode, String type, Long actorId, Long custodianAfter, String location, String detail) {
        return append(assetCode, type, actorId, roleLabel(actorId), custodianAfter, location, detail);
    }

    @Transactional
    public CustodyChainDto recordManual(AuthUser me, String code, CustodyEventRequest r) {
        String type = r.eventType().trim().toUpperCase(Locale.ROOT);
        if (!MANUAL_TYPES.contains(type)) throw ApiException.badRequest("Event type must be one of " + new TreeSet<>(MANUAL_TYPES));
        Passport p = passports.findByPassportCode(code).orElseThrow(() -> ApiException.notFound("Asset not found"));
        requireCanAct(me, p);
        Long custodian = null; String detail = r.detail();
        if ("HANDOVER".equals(type)) {
            if (r.toUserId() == null) throw ApiException.badRequest("HANDOVER needs the receiving user's id");
            AppUser to = lookup.user(r.toUserId());
            if (to.getStatus() != AccountStatus.ACTIVE) throw ApiException.badRequest("The receiving account is not active");
            if (to.getId().equals(me.id())) throw ApiException.badRequest("You cannot hand an asset over to yourself");
            custodian = to.getId(); detail = "Handed over to user #" + to.getId() + (r.detail() == null || r.detail().isBlank() ? "" : ": " + r.detail());
        }
        append(code, type, me.id(), roleLabel(me.id()), custodian, r.location(), detail);
        if (custodian != null) notifications.notify(custodian, "Asset handed over to you", p.getName() + " (" + code + ") is now in your custody.", "CUSTODY");
        return chain(me, code);
    }

    /** Resolves a scanned QR payload, returns the chain and (for authorised actors only) records the scan so physical checks are part of the audit trail. */
    @Transactional
    public CustodyChainDto scan(AuthUser me, CustodyScanRequest r) {
        String code = assetCodeFromPayload(r.payload());
        Passport p = passports.findByPassportCode(code).orElseThrow(() -> ApiException.notFound("No asset with code " + code));
        if (canAct(me, p)) append(code, "SCANNED", me.id(), roleLabel(me.id()), null, r.location(), "QR code scanned by custodian");
        return chain(me, code);
    }

    public static String assetCodeFromPayload(String payload) {
        Matcher m = CODE.matcher(payload == null ? "" : payload.trim().toUpperCase(Locale.ROOT));
        if (!m.find()) throw ApiException.badRequest("Not an E-CYCLE asset QR code");
        return m.group(1);
    }

    // ------------------------------------------------------------------ authorisation
    public boolean canAct(AuthUser me, Passport p) {
        if (me.has(Role.RESOLVER)) return true;
        Long custodian = repo.findFirstByAssetCodeOrderBySeqDesc(p.getPassportCode()).map(CustodyEvent::getCustodianId).orElse(p.getCurrentOwnerId());
        if (me.id().equals(custodian) || me.id().equals(p.getCurrentOwnerId())) return true;
        return pickups.findFirstByListingIdAndStatusIn(p.getListingId() == null ? -1L : p.getListingId(), List.of(PickupStatus.ACCEPTED, PickupStatus.EN_ROUTE, PickupStatus.PICKED_UP, PickupStatus.AT_FACILITY))
                .map(pk -> pk.getRecyclerId().equals(me.id())).orElse(false);
    }
    private void requireCanAct(AuthUser me, Passport p) { if (!canAct(me, p)) throw ApiException.forbidden("Only the current custodian, the owner, the assigned recycler or an admin can record custody events"); }

    // ------------------------------------------------------------------ reading
    @Transactional(readOnly = true)
    public CustodyChainDto chain(AuthUser me, String code) {
        Passport p = passports.findByPassportCode(code).orElseThrow(() -> ApiException.notFound("Asset not found"));
        List<CustodyEvent> list = repo.findByAssetCodeOrderBySeqAsc(code);
        HashChain.Verification v = verifyList(list);
        Long custodian = list.isEmpty() ? p.getCurrentOwnerId() : list.get(list.size() - 1).getCustodianId();
        List<CustodyEntryDto> entries = list.stream().map(e -> new CustodyEntryDto(e.getSeq(), e.getEventType(), e.getActorId() == null ? "System" : lookup.label(e.getActorId(), me),
                e.getActorRole(), e.getCustodianId() == null ? null : lookup.label(e.getCustodianId(), me), e.getLocation(), e.getDetail(), e.getOccurredAt(), e.getPrevHash(), e.getHash())).toList();
        String msg = list.isEmpty() ? "No custody chain yet. It is created automatically on the first recorded event or scan." : v.reason();
        return new CustodyChainDto(code, p.getName(), PassportService.qrPayload(code), custodian == null ? null : lookup.label(custodian, me), v.valid(), msg, v.brokenAtSeq(),
                list.size(), list.isEmpty() ? HashChain.GENESIS : v.headHash(), entries);
    }
    @Transactional(readOnly = true)
    public HashChain.Verification verify(String code) { return verifyList(repo.findByAssetCodeOrderBySeqAsc(code)); }
    private static HashChain.Verification verifyList(List<CustodyEvent> list) { return HashChain.verify(list.stream().map(e -> toEntry(e, null)).toList()); }

    /** Latest (seq, hash) anchor used when issuing a certificate; empty when the asset has no chain. */
    @Transactional(readOnly = true)
    public Optional<CustodyEvent> head(String code) { return repo.findFirstByAssetCodeOrderBySeqDesc(code); }
    /** True when the entry at seq still carries the anchored hash and the whole chain verifies (detects tail truncation and rewrites after issuance). */
    @Transactional(readOnly = true)
    public boolean anchorIntact(String code, Integer seq, String hash) {
        if (seq == null || hash == null) return true;
        List<CustodyEvent> list = repo.findByAssetCodeOrderBySeqAsc(code);
        return verifyList(list).valid() && list.stream().anyMatch(e -> e.getSeq() == seq && e.getHash().equals(hash));
    }

    // ------------------------------------------------------------------ helpers
    public String passportCodeForListing(Long listingId) { return passports.findByListingId(listingId).map(Passport::getPassportCode).orElse(null); }
    public String roleLabel(Long userId) {
        if (userId == null) return "SYSTEM";
        return users.findById(userId).map(u -> {
            Set<Role> r = u.roleSet();
            if (r.contains(Role.RESOLVER)) return "ADMIN";
            if (r.contains(Role.RESOLVER)) return "RESOLVER";
            if (r.contains(Role.BUYER) && buyers.findByUserId(userId).map(b -> b.getBuyerType() == BuyerType.RECYCLER && b.isVerified()).orElse(false)) return "RECYCLER";
            return r.contains(Role.SELLER) ? "SELLER" : "BUYER";
        }).orElse("UNKNOWN");
    }
}
