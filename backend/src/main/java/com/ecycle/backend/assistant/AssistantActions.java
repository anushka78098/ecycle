package com.ecycle.backend.assistant;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.mongo.AssistantPendingActionDoc;
import com.ecycle.backend.mongo.AssistantPendingActionRepository;
import com.ecycle.backend.service.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Everything the assistant can do, independent of any LLM. Read-only lookups run immediately as the signed-in user; actions that change data (book / cancel a pickup, redeem a reward)
 * are only ever PROPOSED here and executed after the user presses Confirm in the UI, through the same services (and therefore the same authorization rules) as the REST API.
 */
@Service @RequiredArgsConstructor
public class AssistantActions {
    static final Duration PENDING_TTL = Duration.ofMinutes(10);
    private final EcoService eco; private final PickupService pickups; private final RecyclerService recyclers; private final CertificateService certificates; private final AssistantPendingActionRepository pending;

    // ------------------------------------------------------------------ read-only
    public String ecoSummary(AuthUser me) {
        EcoDashboardDto d = eco.dashboard(me);
        return String.format(Locale.ROOT, "Balance %d eco-points (lifetime %d), level %d \"%s\", leaderboard rank %s. E-waste diverted %.1f kg, CO2e avoided about %.1f kg (an estimate), %d pickups completed. Badges earned: %s.",
                d.balance(), d.lifetimePoints(), d.level(), d.levelName(), d.rank() > 0 ? "#" + d.rank() : "n/a", d.ewasteDivertedKg(), d.carbonAvoidedKg(), d.pickupsCompleted(),
                d.badges().stream().filter(BadgeDto::earned).map(BadgeDto::name).collect(Collectors.joining(", ")).replaceAll("^$", "none yet"));
    }
    public String pickupsSummary(AuthUser me) {
        List<PickupDto> l = pickups.mine(me, false);
        if (l.isEmpty()) return "You have no pickup bookings.";
        return l.stream().limit(8).map(p -> p.trackingCode() + " - " + p.itemsDescription() + " at " + p.facilityName() + ": " + p.status() + (p.scheduledAt() != null ? ", scheduled " + p.scheduledAt() : "")).collect(Collectors.joining("\n"));
    }
    public String pickupStatus(AuthUser me, String code) {
        PickupDto p = pickups.track(me, code);
        return p.trackingCode() + ": " + p.status() + " (" + p.itemsDescription() + ", " + p.facilityName() + ")" + (p.statusNote() != null ? ". Note: " + p.statusNote() : "");
    }
    public String findRecyclers(String city, String category) {
        String c = city == null ? "" : city.trim().toLowerCase(Locale.ROOT);
        List<RecyclerLocationDto> l = recyclers.search(null, null, null, category).stream().filter(r -> c.isEmpty() || r.city().toLowerCase(Locale.ROOT).contains(c)).limit(8).toList();
        if (l.isEmpty()) return "No active recycler facilities match" + (c.isEmpty() ? "." : " in " + city + ".");
        return l.stream().map(r -> "#" + r.id() + " " + r.facilityName() + ", " + r.city() + " (" + r.address() + "). Accepts: " + nz(r.acceptedCategories()) + ". Hours: " + nz(r.openingHours())).collect(Collectors.joining("\n"));
    }
    public String rewards(AuthUser me) {
        List<RewardDto> l = eco.rewards(me, false);
        return l.isEmpty() ? "No rewards are available right now." : l.stream().map(r -> "#" + r.id() + " " + r.title() + " - " + r.costPoints() + " points" + (r.affordable() ? "" : " (not enough points yet)")).collect(Collectors.joining("\n"));
    }
    public String estimate(Long categoryId, double weightKg, int quantity, String pathway) {
        Pathway p; try { p = Pathway.valueOf(pathway == null ? "RECYCLE" : pathway.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { p = Pathway.RECYCLE; }
        EstimateDto e = eco.estimate(categoryId, BigDecimal.valueOf(weightKg), Math.max(1, quantity), p);
        return String.format(Locale.ROOT, "Estimate: %.1f kg e-waste diverted, about %.1f kg CO2e avoided (roughly %.1f tree-years of absorption), about %d eco-points. This is an estimate from average conversion factors.", e.ewasteDivertedKg(), e.carbonAvoidedKg(), e.equivalents().treeYears(), e.estimatedPoints());
    }
    public String myCertificates(AuthUser me) {
        List<CertificateDto> l = certificates.mine(me);
        return l.isEmpty() ? "You have no certificates yet." : l.stream().limit(8).map(c -> c.certificateNo() + " (" + c.type() + ", " + c.issuedAt() + ")").collect(Collectors.joining("\n"));
    }

    // ------------------------------------------------------------------ proposals (nothing is changed here)
    public PendingActionDto proposeBook(AuthUser me, String conv, long locationId, String items, double weightKg, int quantity, String when, boolean dataBearing, String address) {
        if (me.has(Role.RESOLVER) && me.roles().size() == 1) throw ApiException.forbidden("Administrators cannot book pickups");
        if (items == null || items.isBlank() || items.length() > 500) throw ApiException.badRequest("Describe the items (max 500 characters)");
        if (weightKg <= 0 || weightKg > 5000) throw ApiException.badRequest("Weight must be between 0.01 and 5000 kg");
        if (quantity < 1 || quantity > 1000) throw ApiException.badRequest("Quantity must be between 1 and 1000");
        Instant at = parseWhen(when);
        RecyclerLocationDto loc = recyclers.search(null, null, null, null).stream().filter(r -> r.id() == locationId).findFirst().orElseThrow(() -> ApiException.notFound("No active facility with id " + locationId));
        Map<String, String> p = new LinkedHashMap<>();
        p.put("locationId", String.valueOf(locationId)); p.put("items", items.trim()); p.put("weightKg", String.valueOf(weightKg)); p.put("quantity", String.valueOf(quantity));
        p.put("preferredAt", at.toString()); p.put("dataBearing", String.valueOf(dataBearing)); if (address != null && !address.isBlank()) p.put("address", address.trim());
        return store(me, conv, PendingActionType.BOOK_PICKUP, p, "Book a pickup with " + loc.facilityName() + " for " + quantity + " x " + items.trim() + " (~" + weightKg + " kg) on " + at + (dataBearing ? ", data-bearing" : ""));
    }
    public PendingActionDto proposeCancel(AuthUser me, String conv, String trackingCode) {
        PickupDto d = pickups.track(me, trackingCode);
        if (!d.requesterId().equals(me.id())) throw ApiException.forbidden("You can only cancel your own pickups");
        return store(me, conv, PendingActionType.CANCEL_PICKUP, new LinkedHashMap<>(Map.of("pickupId", String.valueOf(d.id()), "code", d.trackingCode())), "Cancel pickup " + d.trackingCode() + " (" + d.itemsDescription() + ", currently " + d.status() + ")");
    }
    public PendingActionDto proposeRedeem(AuthUser me, String conv, long rewardId) {
        RewardDto r = eco.rewards(me, false).stream().filter(x -> x.id() == rewardId).findFirst().orElseThrow(() -> ApiException.notFound("No such reward"));
        if (!r.affordable()) throw ApiException.conflict("You do not have enough eco-points for " + r.title());
        return store(me, conv, PendingActionType.REDEEM_REWARD, new LinkedHashMap<>(Map.of("rewardId", String.valueOf(rewardId))), "Redeem \"" + r.title() + "\" for " + r.costPoints() + " eco-points");
    }
    private PendingActionDto store(AuthUser me, String conv, PendingActionType t, Map<String, String> params, String summary) {
        AssistantPendingActionDoc d = new AssistantPendingActionDoc();
        d.setUserId(me.id()); d.setConversationId(conv); d.setType(t); d.setParams(params); d.setSummary(summary); d.setExpiresAt(Instant.now().plus(PENDING_TTL));
        pending.save(d);
        return dto(d);
    }

    // ------------------------------------------------------------------ confirmation (called only from the REST endpoint the UI button uses)
    public ActionResultDto confirm(AuthUser me, String id) {
        AssistantPendingActionDoc d = owned(me, id);
        if (!"PENDING".equals(d.getStatus())) return new ActionResultDto(false, "This action is no longer pending (" + d.getStatus().toLowerCase(Locale.ROOT) + ").", d.getStatus());
        if (d.getExpiresAt().isBefore(Instant.now())) { d.setStatus("EXPIRED"); pending.save(d); return new ActionResultDto(false, "This request expired. Ask the assistant again.", "EXPIRED"); }
        d.setStatus("CONFIRMED"); pending.save(d);   // claimed before running so a double click cannot run it twice
        try {
            String msg = switch (d.getType()) {
                case BOOK_PICKUP -> { Map<String, String> p = d.getParams();
                    PickupDto r = pickups.request(me, new PickupRequest(Long.valueOf(p.get("locationId")), null, null, p.get("items"), Integer.parseInt(p.get("quantity")), new BigDecimal(p.get("weightKg")),
                            Boolean.parseBoolean(p.get("dataBearing")), false, p.get("address"), null, null, null, Instant.parse(p.get("preferredAt"))));
                    yield "Pickup booked. Tracking code " + r.trackingCode() + "; the recycler has been notified."; }
                case CANCEL_PICKUP -> { pickups.updateStatus(me, Long.valueOf(d.getParams().get("pickupId")), new PickupStatusRequest(PickupStatus.CANCELLED, null, "Cancelled via assistant", null, null, null)); yield "Pickup " + d.getParams().get("code") + " cancelled."; }
                case REDEEM_REWARD -> { RedemptionDto r = eco.redeem(me, Long.valueOf(d.getParams().get("rewardId"))); yield "Redeemed " + r.rewardTitle() + ". Voucher code: " + r.voucherCode(); }
            };
            d.setResultMessage(msg); pending.save(d);
            return new ActionResultDto(true, msg, "CONFIRMED");
        } catch (RuntimeException e) {
            d.setStatus("FAILED"); d.setResultMessage(e.getMessage()); pending.save(d);
            return new ActionResultDto(false, e instanceof ApiException ? e.getMessage() : "The action could not be completed.", "FAILED");
        }
    }
    public ActionResultDto cancel(AuthUser me, String id) {
        AssistantPendingActionDoc d = owned(me, id);
        if (!"PENDING".equals(d.getStatus())) return new ActionResultDto(false, "This action is no longer pending.", d.getStatus());
        d.setStatus("CANCELLED"); pending.save(d);
        return new ActionResultDto(true, "Cancelled. Nothing was changed.", "CANCELLED");
    }
    private AssistantPendingActionDoc owned(AuthUser me, String id) {
        AssistantPendingActionDoc d = pending.findById(id == null ? "" : id).orElseThrow(() -> ApiException.notFound("Action not found"));
        if (!d.getUserId().equals(me.id())) throw ApiException.notFound("Action not found");
        return d;
    }
    public static PendingActionDto dto(AssistantPendingActionDoc d) { return new PendingActionDto(d.getId(), d.getType(), d.getSummary(), d.getExpiresAt(), d.getStatus()); }

    /** Accepts ISO date-time, ISO date (taken as 10:00 UTC) or "tomorrow". Must not be in the past. */
    static Instant parseWhen(String s) {
        if (s == null || s.isBlank()) throw ApiException.badRequest("Ask the user for a preferred pickup date");
        String t = s.trim(); Instant at;
        try {
            if (t.equalsIgnoreCase("tomorrow")) at = LocalDate.now(ZoneOffset.UTC).plusDays(1).atTime(10, 0).toInstant(ZoneOffset.UTC);
            else if (t.length() <= 10) at = LocalDate.parse(t).atTime(10, 0).toInstant(ZoneOffset.UTC);
            else at = t.endsWith("Z") || t.matches(".*[+-]\\d\\d:\\d\\d$") ? OffsetDateTime.parse(t).toInstant() : LocalDateTime.parse(t).toInstant(ZoneOffset.UTC);
        } catch (RuntimeException e) { throw ApiException.badRequest("Date must look like 2026-12-01 or 2026-12-01T10:00"); }
        if (at.isBefore(Instant.now())) throw ApiException.badRequest("The pickup date must be in the future");
        return at;
    }
    private static String nz(String s) { return s == null || s.isBlank() ? "not specified" : s; }
}
