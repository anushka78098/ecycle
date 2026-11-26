package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.engine.GeoMath;
import com.ecycle.backend.engine.PickupFlow;
import com.ecycle.backend.engine.PickupFlow.Actor;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pickup booking and tracking. Every status change is validated by PickupFlow, written to an append-only event log, mirrored into the chain of custody and pushed live over STOMP. */
@Service @RequiredArgsConstructor
public class PickupService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Set<PickupStatus> ACTIVE = EnumSet.of(PickupStatus.REQUESTED, PickupStatus.ACCEPTED, PickupStatus.EN_ROUTE, PickupStatus.PICKED_UP, PickupStatus.AT_FACILITY);
    private final PickupRepository pickups; private final PickupEventRepository events; private final RecyclerService recyclers; private final ListingRepository listings;
    private final PassportRepository passports; private final CustodyService custody; private final CertificateService certificates; private final CertificateRepository certs;
    private final SanitizationWorkflowService wipes; private final EnvironmentalService environmental; private final EcoService eco; private final NotificationService notifications;
    private final EnvironmentalFactorRepository factors; private final LiveService live; private final AuditService audit; private final Lookup lookup; private final SecureRandom rnd = new SecureRandom();

    // ------------------------------------------------------------------ booking
    @Transactional
    public PickupDto request(AuthUser me, PickupRequest r) {
        if (me.has(Role.RESOLVER) && me.roles().size() == 1) throw ApiException.forbidden("Administrators cannot book pickups");
        RecyclerLocation loc = recyclers.require(r.locationId());
        if (!loc.isActive()) throw ApiException.conflict("This facility is not accepting pickups");
        if (loc.getRecyclerId().equals(me.id())) throw ApiException.badRequest("You cannot book a pickup with your own facility");
        if (r.preferredAt().isBefore(Instant.now().minusSeconds(300))) throw ApiException.badRequest("Preferred pickup time must be in the future");
        if ((r.pickupLat() == null) != (r.pickupLng() == null) || (r.pickupLat() != null && !GeoMath.validLatLon(r.pickupLat(), r.pickupLng()))) throw ApiException.badRequest("Invalid pickup coordinates");
        Pickup p = new Pickup();
        p.setRequesterId(me.id()); p.setRecyclerId(loc.getRecyclerId()); p.setLocationId(loc.getId()); p.setCategoryId(r.categoryId());
        if (r.listingId() != null) {
            Listing l = listings.findById(r.listingId()).orElseThrow(() -> ApiException.notFound("Listing not found"));
            Passport pass = passports.findByListingId(l.getId()).orElse(null);
            boolean owns = l.getSellerId().equals(me.id()) || (pass != null && me.id().equals(pass.getCurrentOwnerId()));
            if (!owns) throw ApiException.forbidden("You can only book pickups for your own items");
            if (pickups.findFirstByListingIdAndStatusIn(l.getId(), ACTIVE).isPresent()) throw ApiException.conflict("An active pickup already exists for this item");
            if (l.isRequiresSanitization()) p.setDataBearing(true);
            p.setListingId(l.getId()); p.setPassportCode(pass == null ? null : pass.getPassportCode()); if (p.getCategoryId() == null) p.setCategoryId(l.getCategoryId());
            if (l.getHazardLevel() != null && l.getHazardLevel() != HazardLevel.LOW) p.setHazardous(true);
        }
        p.setDataBearing(p.isDataBearing() || r.dataBearing()); p.setHazardous(p.isHazardous() || r.hazardous());
        p.setItemsDescription(r.itemsDescription().trim()); p.setQuantity(r.quantity()); p.setEstWeightKg(r.estWeightKg());
        p.setPickupAddress(r.pickupAddress()); p.setPickupLat(r.pickupLat()); p.setPickupLng(r.pickupLng()); p.setContactPhone(r.contactPhone()); p.setPreferredAt(r.preferredAt());
        p.setEstCarbonKg(BigDecimal.valueOf(eco.recycleCarbonKg(p.getCategoryId(), r.estWeightKg().doubleValue())).setScale(3, RoundingMode.HALF_UP));
        p.setTrackingCode(newCode());
        pickups.save(p);
        addEvent(p, PickupStatus.REQUESTED, me.id(), "Pickup requested", null, null);
        audit.log(me.id(), "PICKUP_REQUESTED", "pickups", p.getId(), p.getTrackingCode() + " at " + loc.getFacilityName());
        notifications.notify(p.getRecyclerId(), "New pickup request " + p.getTrackingCode(), p.getItemsDescription() + " (" + p.getEstWeightKg() + " kg) for " + loc.getFacilityName(), "PICKUP");
        push(p);
        return dto(p, Actor.REQUESTER);
    }
    private String newCode() {
        String c;
        do { StringBuilder sb = new StringBuilder("PK-"); for (int i = 0; i < 7; i++) sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length()))); c = sb.toString(); } while (pickups.findByTrackingCode(c).isPresent());
        return c;
    }

    // ------------------------------------------------------------------ status changes
    @Transactional
    public PickupDto updateStatus(AuthUser me, Long id, PickupStatusRequest r) {
        Pickup p = pickups.lockById(id).orElseThrow(() -> ApiException.notFound("Pickup not found"));
        Actor a = actor(me, p);
        PickupStatus to = r.status();
        if (to == PickupStatus.COMPLETED) throw ApiException.badRequest("Use the verification step to complete a pickup");
        if (!PickupFlow.canTransition(p.getStatus(), to, a)) throw ApiException.conflict("Cannot move a pickup from " + p.getStatus() + " to " + to + " as " + a.name().toLowerCase(Locale.ROOT));
        if (to == PickupStatus.ACCEPTED) { if (r.scheduledAt() != null && r.scheduledAt().isBefore(Instant.now().minusSeconds(300))) throw ApiException.badRequest("Scheduled time is in the past"); p.setScheduledAt(r.scheduledAt() != null ? r.scheduledAt() : p.getPreferredAt()); }
        if ((to == PickupStatus.PICKED_UP || to == PickupStatus.AT_FACILITY) && p.getPassportCode() != null) requireScan(p, r.scanPayload());
        if (r.lat() != null && r.lng() != null && !GeoMath.validLatLon(r.lat(), r.lng())) throw ApiException.badRequest("Invalid coordinates");
        p.setStatus(to); p.setStatusNote(r.note());
        if (r.lat() != null && r.lng() != null) { p.setLastLat(r.lat()); p.setLastLng(r.lng()); p.setLastPingAt(Instant.now()); }
        pickups.save(p);
        addEvent(p, to, me.id(), r.note(), r.lat(), r.lng());
        recordCustody(p, to, me, r.note());
        audit.log(me.id(), "PICKUP_" + to.name(), "pickups", p.getId(), p.getTrackingCode());
        Long other = a == Actor.RECYCLER ? p.getRequesterId() : p.getRecyclerId();
        notifications.notify(other, "Pickup " + p.getTrackingCode() + ": " + label(to), r.note() == null ? "Status changed to " + label(to) + "." : r.note(), "PICKUP");
        if (a == Actor.ADMIN) { notifications.notify(p.getRequesterId(), "Pickup " + p.getTrackingCode() + ": " + label(to), "Updated by an administrator.", "PICKUP"); }
        push(p);
        return dto(p, a);
    }

    /** Recycler's live position while EN_ROUTE. */
    @Transactional
    public PickupDto ping(AuthUser me, Long id, PickupPingRequest r) {
        Pickup p = pickups.lockById(id).orElseThrow(() -> ApiException.notFound("Pickup not found"));
        if (!p.getRecyclerId().equals(me.id())) throw ApiException.notFound("Pickup not found");
        if (p.getStatus() != PickupStatus.EN_ROUTE) throw ApiException.conflict("Location can only be shared while en route");
        if (!GeoMath.validLatLon(r.lat(), r.lng())) throw ApiException.badRequest("Invalid coordinates");
        p.setLastLat(r.lat()); p.setLastLng(r.lng()); p.setLastPingAt(Instant.now()); pickups.save(p);
        push(p);
        return dto(p, Actor.RECYCLER);
    }

    /** Recycler completes the job: measured weight, treatment, data-wipe evidence for data-bearing devices, then certificates + eco-points. */
    @Transactional
    public PickupDto verify(AuthUser me, Long id, PickupVerifyRequest r) {
        Pickup p = pickups.lockById(id).orElseThrow(() -> ApiException.notFound("Pickup not found"));
        if (!p.getRecyclerId().equals(me.id())) throw ApiException.notFound("Pickup not found");
        if (p.getStatus() != PickupStatus.AT_FACILITY) throw ApiException.conflict("The pickup must be marked 'at facility' before it can be verified");
        if (p.getPassportCode() != null) requireScan(p, r.scanPayload());
        BigDecimal est = p.getEstWeightKg();
        if (est != null && r.actualWeightKg().compareTo(est.multiply(BigDecimal.TEN)) > 0) throw ApiException.badRequest("Measured weight is more than ten times the estimate; check the figure or ask the requester to correct the booking");
        SanitizationChecklist wipe = null;
        if (p.isDataBearing()) {
            if (r.dataDestructionMethod() == null || r.dataDestructionMethod().isBlank()) throw ApiException.badRequest("A data destruction method is required for data-bearing devices");
            wipe = wipes.recordForPickup(me, p, r.dataDestructionMethod(), r.deviceSerial(), r.checklistItems());
        }
        p.setActualWeightKg(r.actualWeightKg()); p.setTreatment(r.treatment()); p.setVerificationNote(r.note()); p.setStatus(PickupStatus.COMPLETED); p.setCompletedAt(Instant.now()); p.setStatusNote(r.note());
        double kg = r.actualWeightKg().doubleValue(), carbon = eco.recycleCarbonKg(p.getCategoryId(), kg);
        pickups.save(p);
        addEvent(p, PickupStatus.COMPLETED, me.id(), "Verified: " + r.actualWeightKg() + " kg, " + r.treatment(), null, null);
        recordCustody(p, PickupStatus.COMPLETED, me, r.treatment());
        audit.log(me.id(), "PICKUP_VERIFIED", "pickups", p.getId(), p.getTrackingCode() + " " + r.actualWeightKg() + " kg");

        RecyclerLocation loc = recyclers.require(p.getLocationId());
        Map<String, String> d = new TreeMap<>();
        d.put("items", p.getItemsDescription() + (p.getQuantity() > 1 ? " x" + p.getQuantity() : "")); d.put("weightKg", r.actualWeightKg().toPlainString()); d.put("treatment", r.treatment());
        d.put("facility", loc.getFacilityName()); d.put("facilityAddress", loc.getAddress() + ", " + loc.getCity()); d.put("pickup", p.getTrackingCode()); d.put("carbonAvoidedKg", String.format(Locale.ROOT, "%.1f", carbon));
        certificates.issue(CertificateType.RECYCLING, null, p.getRequesterId(), me.id(), p.getListingId(), p.getId(), null, p.getPassportCode(), d);
        if (wipe != null) {
            Map<String, String> w = new TreeMap<>(d);
            w.put("dataDestructionMethod", wipe.getMethod()); if (wipe.getDeviceSerial() != null) w.put("serial", wipe.getDeviceSerial()); w.put("verification", "Checklist completed by the recycler (" + wipe.getItems().split(",").length + " steps)");
            certificates.issue(CertificateType.DATA_DESTRUCTION, null, p.getRequesterId(), me.id(), p.getListingId(), p.getId(), null, p.getPassportCode(), w);
        }
        boolean listingImpact = false;
        if (p.getListingId() != null) {
            Listing l = listings.findById(p.getListingId()).orElse(null);
            if (l != null && factors.findByCategoryId(l.getCategoryId()).isPresent()) {   // checked first: an exception inside the shared transaction would roll the whole verification back
                environmental.compute(l, null, Pathway.RECYCLE, me.id(), r.actualWeightKg().divide(BigDecimal.valueOf(Math.max(1, l.getQuantity())), 3, RoundingMode.HALF_UP));
                listingImpact = true;
            }
        }
        eco.onPickupCompleted(p, kg, carbon, listingImpact);
        notifications.notify(p.getRequesterId(), "Pickup " + p.getTrackingCode() + " completed", "Your recycling certificate" + (wipe != null ? " and data destruction certificate are" : " is") + " ready.", "PICKUP");
        push(p);
        return dto(p, Actor.RECYCLER);
    }

    // ------------------------------------------------------------------ reads
    @Transactional(readOnly = true)
    public List<PickupDto> mine(AuthUser me, boolean asRecycler) {
        List<Pickup> l = me.has(Role.RESOLVER) && !asRecycler ? pickups.findTop300ByOrderByCreatedAtDesc() : asRecycler ? pickups.findByRecyclerIdOrderByCreatedAtDesc(me.id()) : pickups.findByRequesterIdOrderByCreatedAtDesc(me.id());
        return l.stream().map(p -> dto(p, actor(me, p))).toList();
    }
    @Transactional(readOnly = true)
    public PickupDto get(AuthUser me, Long id) { Pickup p = pickups.findById(id).orElseThrow(() -> ApiException.notFound("Pickup not found")); return dto(p, actor(me, p)); }
    @Transactional(readOnly = true)
    public PickupDto track(AuthUser me, String code) {
        Pickup p = pickups.findByTrackingCode(code == null ? "" : code.trim().toUpperCase(Locale.ROOT)).orElseThrow(() -> ApiException.notFound("Pickup not found"));
        return dto(p, actor(me, p));
    }

    /** Wipe checklist template for the device category of this pickup (recycler fills it when completing a data-bearing pickup). */
    @Transactional(readOnly = true)
    public ChecklistTemplateDto wipeTemplate(AuthUser me, Long id) {
        Pickup p = pickups.findById(id).orElseThrow(() -> ApiException.notFound("Pickup not found"));
        actor(me, p);
        return wipes.templateFor(p.getListingId(), p.getCategoryId());
    }

    // ------------------------------------------------------------------ helpers
    private Actor actor(AuthUser me, Pickup p) {
        if (me.id().equals(p.getRecyclerId())) return Actor.RECYCLER;
        if (me.id().equals(p.getRequesterId())) return Actor.REQUESTER;
        if (me.has(Role.RESOLVER)) return Actor.ADMIN;
        throw ApiException.notFound("Pickup not found");
    }
    private void requireScan(Pickup p, String payload) {
        if (payload == null || payload.isBlank()) throw ApiException.badRequest("Scan the item's QR code (or enter its asset code) to confirm you have the right device");
        String code = CustodyService.assetCodeFromPayload(payload);
        if (!p.getPassportCode().equals(code)) throw ApiException.conflict("The scanned code does not match the item on this pickup");
    }
    private void recordCustody(Pickup p, PickupStatus to, AuthUser me, String note) {
        if (p.getPassportCode() == null) return;
        RecyclerLocation loc = recyclers.require(p.getLocationId());
        String type = switch (to) { case ACCEPTED -> "PICKUP_ACCEPTED"; case EN_ROUTE -> "TRANSPORT_STARTED"; case PICKED_UP -> "PICKED_UP"; case AT_FACILITY -> "RECEIVED_AT_FACILITY"; case COMPLETED -> "RECYCLED";
            case CANCELLED -> "PICKUP_CANCELLED"; case REJECTED -> "PICKUP_REJECTED"; default -> null; };
        if (type == null) return;
        Long custodian = to == PickupStatus.PICKED_UP || to == PickupStatus.AT_FACILITY || to == PickupStatus.COMPLETED ? p.getRecyclerId() : null;
        custody.recordSystem(p.getPassportCode(), type, me.id(), custodian, (to == PickupStatus.AT_FACILITY || to == PickupStatus.COMPLETED) ? loc.getFacilityName() : p.getPickupAddress(), "Pickup " + p.getTrackingCode() + (note == null || note.isBlank() ? "" : ": " + note));
    }
    private void addEvent(Pickup p, PickupStatus s, Long actor, String note, Double lat, Double lng) {
        PickupEvent e = new PickupEvent(); e.setPickupId(p.getId()); e.setStatus(s); e.setActorId(actor); e.setNote(note == null ? null : (note.length() > 300 ? note.substring(0, 300) : note)); e.setLat(lat); e.setLng(lng); events.save(e);
    }
    private void push(Pickup p) {
        live.push(p.getRequesterId(), "PICKUP", Map.of("id", p.getId(), "trackingCode", p.getTrackingCode(), "status", p.getStatus().name(), "lat", p.getLastLat() == null ? "" : p.getLastLat(), "lng", p.getLastLng() == null ? "" : p.getLastLng()));
        live.push(p.getRecyclerId(), "PICKUP", Map.of("id", p.getId(), "trackingCode", p.getTrackingCode(), "status", p.getStatus().name()));
    }
    static String label(PickupStatus s) { return switch (s) { case REQUESTED -> "requested"; case ACCEPTED -> "accepted"; case EN_ROUTE -> "recycler on the way"; case PICKED_UP -> "picked up"; case AT_FACILITY -> "arrived at facility"; case COMPLETED -> "completed"; case REJECTED -> "declined"; case CANCELLED -> "cancelled"; }; }

    private PickupDto dto(Pickup p, Actor a) {
        RecyclerLocation loc = recyclers.require(p.getLocationId());
        List<PickupEventDto> ev = events.findByPickupIdOrderByIdAsc(p.getId()).stream()
                .map(e -> new PickupEventDto(e.getStatus(), e.getActorId() == null ? "System" : (e.getActorId().equals(p.getRecyclerId()) ? "Recycler" : e.getActorId().equals(p.getRequesterId()) ? "Requester" : "Administrator"), e.getNote(), e.getLat(), e.getLng(), e.getCreatedAt())).toList();
        List<String> nos = certs.findByPickupIdOrderByIdAsc(p.getId()).stream().map(Certificate::getCertificateNo).toList();
        String cat = p.getCategoryId() == null ? null : lookup.category(p.getCategoryId()).getName();
        List<String> next = new ArrayList<>(PickupFlow.allowedNext(p.getStatus(), a).stream().map(Enum::name).toList());
        if (a == Actor.RECYCLER && p.getStatus() == PickupStatus.AT_FACILITY) next.add("VERIFY");
        return new PickupDto(p.getId(), p.getTrackingCode(), p.getRequesterId(), lookup.name(p.getRequesterId()), p.getRecyclerId(), lookup.name(p.getRecyclerId()), loc.getId(), loc.getFacilityName(),
                loc.getLatitude(), loc.getLongitude(), p.getListingId(), p.getPassportCode(), cat, p.getItemsDescription(), p.getQuantity(), p.getEstWeightKg(), p.getActualWeightKg(), p.isDataBearing(), p.isHazardous(),
                p.getPickupAddress(), p.getPickupLat(), p.getPickupLng(), p.getContactPhone(), p.getPreferredAt(), p.getScheduledAt(), p.getStatus(), p.getStatusNote(),
                p.getLastLat(), p.getLastLng(), p.getLastPingAt(), p.getEstCarbonKg(), p.getTreatment(), next, ev, nos, p.getCreatedAt());
    }
}
