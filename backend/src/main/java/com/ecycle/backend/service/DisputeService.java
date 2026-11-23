package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Feature: evidence-based dispute management. Resolvers decide; money moves only through RefundService as a system action after the decision is recorded. */
@Service @RequiredArgsConstructor
public class DisputeService {
    private static final Set<DisputeStatus> FINAL = Set.of(DisputeStatus.RESOLVED, DisputeStatus.REJECTED);
    private final DisputeRepository disputes; private final DisputeAssignmentRepository assignments; private final DisputeEvidenceRepository evidence;
    private final DisputeMessageRepository messages; private final DisputeResolutionRepository resolutions; private final PurchaseOrderRepository orders;
    private final ListingRepository listings; private final PaymentRepository payments; private final ShipmentRepository shipments; private final UserRepository users;
    private final SellerProfileRepository sellers; private final BuyerProfileRepository buyers; private final Lookup lookup; private final FileStorageService storage;
    private final RefundService refunds; private final NotificationService notifications; private final AuditService audit;

    private Dispute load(Long id) { return disputes.findById(id).orElseThrow(() -> ApiException.notFound("Dispute not found")); }
    private static boolean party(AuthUser me, Dispute d) { return me.id().equals(d.getRaisedBy()) || me.id().equals(d.getAgainstUser()); }
    private static void requireView(AuthUser me, Dispute d) { if (!party(me, d) && !me.isStaff()) throw ApiException.forbidden("You are not part of this dispute"); }
    private static void requireNotFinal(Dispute d) { if (FINAL.contains(d.getStatus())) throw ApiException.conflict("This dispute is already " + d.getStatus()); }
    private static void requireHandler(AuthUser me, Dispute d) {
        if (!me.has(Role.RESOLVER)) throw ApiException.forbidden("Only resolvers can perform dispute actions");
        if (d.getResolverId() == null || !me.id().equals(d.getResolverId()))
            throw ApiException.forbidden("Only the assigned resolver can perform this action");
    }

    @Transactional
    public DisputeDto create(AuthUser me, DisputeRequest r) {
        PurchaseOrder o = orders.findById(r.orderId()).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!me.id().equals(o.getBuyerId()) && !me.id().equals(o.getSellerId())) throw ApiException.forbidden("Only the buyer or seller of an order can raise a dispute");
        if (o.getStatus() != OrderStatus.PAID && o.getStatus() != OrderStatus.SHIPPED && o.getStatus() != OrderStatus.DELIVERED)
            throw ApiException.conflict("Disputes can only be raised for paid, shipped or delivered orders (status: " + o.getStatus() + ")");
        Dispute d = new Dispute();
        d.setOrderId(o.getId()); d.setRaisedBy(me.id()); d.setAgainstUser(me.id().equals(o.getBuyerId()) ? o.getSellerId() : o.getBuyerId());
        d.setCategory(r.category()); d.setDescription(r.description());
        BigDecimal amt = o.getAmount();
        d.setPriority(r.category() == DisputeCategory.PAYMENT_ISSUE || amt.compareTo(new BigDecimal("100000")) >= 0 ? DisputePriority.HIGH
                : amt.compareTo(new BigDecimal("5000")) < 0 ? DisputePriority.LOW : DisputePriority.MEDIUM);
        disputes.save(d);   // unique partial index guarantees one active dispute per order
        o.setStatus(OrderStatus.DISPUTED);
        audit.log(me.id(), "DISPUTE_RAISED", "disputes", d.getId(), r.category() + " order=" + o.getId());
        notifications.notify(d.getAgainstUser(), "A dispute was raised against you", "Order #" + o.getId() + ": " + r.category() + ". Submit your evidence.", "DISPUTE");
        notifications.notifyRole(Role.RESOLVER, "New dispute #" + d.getId(), r.category() + " (" + d.getPriority() + " priority)", "DISPUTE");
        return dto(d);
    }

    @Transactional
    public EvidenceDto uploadEvidence(AuthUser me, Long id, MultipartFile file, EvidenceCategory category, String description) {
        Dispute d = load(id);
        if (!party(me, d)) throw ApiException.forbidden("Only the buyer or seller in this dispute can submit evidence");
        requireNotFinal(d);
        var s = storage.store(file, "disputes/" + id, FileStorageService.EVIDENCE_TYPES, 10L * 1024 * 1024);
        DisputeEvidence e = new DisputeEvidence();
        e.setDisputeId(id); e.setSubmittedBy(me.id()); e.setCategory(category); e.setDescription(description);
        e.setFilePath(s.path()); e.setOriginalName(s.originalName()); e.setContentType(s.contentType()); e.setSha256(s.sha256());
        evidence.save(e);   // row is immutable (DB trigger); file is addressed by its SHA-256 for tamper detection
        if (d.getStatus() == DisputeStatus.AWAITING_EVIDENCE) d.setStatus(DisputeStatus.UNDER_REVIEW);
        systemNote(d, me.id(), "Evidence submitted: " + category + " \"" + s.originalName() + "\" (sha256 " + s.sha256().substring(0, 12) + "...)");
        audit.log(me.id(), "EVIDENCE_SUBMITTED", "disputes", id, category + " " + s.sha256());
        Long other = me.id().equals(d.getRaisedBy()) ? d.getAgainstUser() : d.getRaisedBy();
        notifications.notify(other, "New evidence in dispute #" + id, "The other party submitted " + category + " evidence.", "DISPUTE");
        if (d.getResolverId() != null) notifications.notify(d.getResolverId(), "New evidence in dispute #" + id, category + " evidence was submitted.", "DISPUTE");
        return evDto(e);
    }
    private void systemNote(Dispute d, Long sender, String text) {
        DisputeMessage m = new DisputeMessage(); m.setDisputeId(d.getId()); m.setSenderId(sender); m.setVisibility(MessageVisibility.PARTIES); m.setBody("[log] " + text); messages.save(m);
    }

    public record EvidenceFile(Resource resource, String contentType, String name) {}
    @Transactional(readOnly = true)
    public EvidenceFile evidenceFile(AuthUser me, Long id, Long evidenceId) {
        Dispute d = load(id); requireView(me, d);
        DisputeEvidence e = evidence.findById(evidenceId).filter(x -> x.getDisputeId().equals(id)).orElseThrow(() -> ApiException.notFound("Evidence not found"));
        return new EvidenceFile(storage.load(e.getFilePath()), e.getContentType(), e.getOriginalName());
    }

    @Transactional
    public MessageDto post(AuthUser me, Long id, MessageRequest r) {
        Dispute d = load(id); requireView(me, d); requireNotFinal(d);
        MessageVisibility vis = r.visibility() == null ? MessageVisibility.PARTIES : r.visibility();
        if (party(me, d) && !me.isStaff()) vis = MessageVisibility.PARTIES;
        else { requireHandler(me, d); }
        DisputeMessage m = new DisputeMessage(); m.setDisputeId(id); m.setSenderId(me.id()); m.setVisibility(vis); m.setBody(r.body()); messages.save(m);
        if (vis == MessageVisibility.EVIDENCE_REQUEST) {
            d.setStatus(DisputeStatus.AWAITING_EVIDENCE);
            notifications.notifyAll(List.of(d.getRaisedBy(), d.getAgainstUser()), "Additional evidence requested", "Dispute #" + id + ": " + r.body(), "DISPUTE");
        } else if (vis == MessageVisibility.PARTIES) {
            if (party(me, d)) { Long other = me.id().equals(d.getRaisedBy()) ? d.getAgainstUser() : d.getRaisedBy(); notifications.notify(other, "New message in dispute #" + id, r.body(), "DISPUTE"); }
            else notifications.notifyAll(List.of(d.getRaisedBy(), d.getAgainstUser()), "Message from resolver, dispute #" + id, r.body(), "DISPUTE");
        }
        audit.log(me.id(), "DISPUTE_MESSAGE", "disputes", id, vis.name());
        return msgDto(m);
    }

    @Transactional
    public DisputeDto assign(AuthUser me, Long id, Long resolverId) {
        Dispute d = load(id); requireNotFinal(d);
        Long target;
        if (!me.has(Role.RESOLVER)) throw ApiException.forbidden("Only resolvers can assign disputes");
        target = resolverId == null ? me.id() : resolverId;
        if (d.getResolverId() != null && !d.getResolverId().equals(me.id()))
            throw ApiException.conflict("Already assigned to another resolver");
        AppUser t = users.findById(target).orElseThrow(() -> ApiException.notFound("Resolver not found"));
        if (!t.hasRole(Role.RESOLVER)) throw ApiException.badRequest("The assignee must be a dispute resolver");
        if (target.equals(d.getRaisedBy()) || target.equals(d.getAgainstUser())) throw ApiException.badRequest("A party to the dispute cannot resolve it");
        DisputeAssignment a = new DisputeAssignment(); a.setDisputeId(id); a.setResolverId(target); a.setAssignedBy(me.id()); assignments.save(a);
        d.setResolverId(target); if (d.getStatus() == DisputeStatus.OPEN) d.setStatus(DisputeStatus.ASSIGNED);
        audit.log(me.id(), "DISPUTE_ASSIGNED", "disputes", id, "resolver=" + target);
        notifications.notifyAll(List.of(d.getRaisedBy(), d.getAgainstUser()), "A resolver was assigned", "Dispute #" + id + " is now being investigated.", "DISPUTE");
        if (!target.equals(me.id())) notifications.notify(target, "Dispute assigned to you", "Dispute #" + id, "DISPUTE");
        return dto(d);
    }

    @Transactional
    public DisputeDto setPriority(AuthUser me, Long id, DisputePriority p) {
        Dispute d = load(id); requireNotFinal(d); requireHandler(me, d);
        DisputePriority old = d.getPriority(); d.setPriority(p);
        audit.log(me.id(), "DISPUTE_PRIORITY", "disputes", id, old + " -> " + p);
        return dto(d);
    }

    @Transactional
    public DisputeDetailDto resolve(AuthUser me, Long id, ResolutionRequest r) {
        Dispute d = load(id); requireNotFinal(d); requireHandler(me, d);
        PurchaseOrder o = orders.findById(d.getOrderId()).orElseThrow();
        BigDecimal paid = payments.findFirstByOrderIdOrderByIdDesc(o.getId())
                .filter(p -> p.getStatus() == PaymentStatus.VERIFIED || p.getStatus() == PaymentStatus.PARTIALLY_REFUNDED).map(Payment::getAmount).orElse(BigDecimal.ZERO);
        BigDecimal refund = switch (r.type()) {
            case FULL_REFUND, RETURN_AND_REFUND -> paid;
            case PARTIAL_REFUND -> { if (r.refundAmount() == null || r.refundAmount().signum() <= 0 || r.refundAmount().compareTo(paid) >= 0) throw ApiException.badRequest("Partial refund must be above 0 and below the paid amount " + paid); yield r.refundAmount(); }
            case MUTUAL_SETTLEMENT, RESOLVED_BUYER_FAVOR -> r.refundAmount() == null ? BigDecimal.ZERO : r.refundAmount();
            case REPLACEMENT, RESOLVED_SELLER_FAVOR -> { if (r.refundAmount() != null && r.refundAmount().signum() != 0) throw ApiException.badRequest(r.type() + " cannot include a refund"); yield BigDecimal.ZERO; }
        };
        if (refund.signum() < 0 || refund.compareTo(paid) > 0) throw ApiException.badRequest("Refund cannot exceed the paid amount " + paid);
        if (refund.signum() > 0) {
            if (paid.signum() == 0) throw ApiException.conflict("There is no verified payment to refund");
            Refund rf = refunds.refund(o, refund, "Dispute #" + id + ": " + r.type(), id, me.id());
            if (rf.getStatus() != RefundStatus.PROCESSED) throw ApiException.conflict("The refund failed at the payment provider; the dispute remains open");
        }
        DisputeResolution res = new DisputeResolution(); res.setDisputeId(id); res.setResolverId(me.id()); res.setResolutionType(r.type()); res.setRefundAmount(refund); res.setDecisionText(r.decisionText());
        resolutions.save(res);
        d.setStatus(DisputeStatus.RESOLVED); d.setResolvedAt(Instant.now());
        restoreOrder(o, refund.compareTo(paid) >= 0 && paid.signum() > 0);
        audit.log(me.id(), "DISPUTE_RESOLVED", "disputes", id, r.type() + " refund=" + refund);
        notifications.notifyAll(List.of(d.getRaisedBy(), d.getAgainstUser()), "Dispute #" + id + " resolved", r.type() + (refund.signum() > 0 ? " (refund " + refund + ")" : "") + ": " + r.decisionText(), "DISPUTE");
        return detail(me, id);
    }

    @Transactional
    public DisputeDetailDto reject(AuthUser me, Long id, String reason) {
        Dispute d = load(id); requireNotFinal(d); requireHandler(me, d);
        d.setStatus(DisputeStatus.REJECTED); d.setResolvedAt(Instant.now());
        DisputeMessage m = new DisputeMessage(); m.setDisputeId(id); m.setSenderId(me.id()); m.setVisibility(MessageVisibility.PARTIES); m.setBody("Dispute rejected: " + reason); messages.save(m);
        restoreOrder(orders.findById(d.getOrderId()).orElseThrow(), false);
        audit.log(me.id(), "DISPUTE_REJECTED", "disputes", id, reason);
        notifications.notifyAll(List.of(d.getRaisedBy(), d.getAgainstUser()), "Dispute #" + id + " rejected", reason, "DISPUTE");
        return detail(me, id);
    }

    private void restoreOrder(PurchaseOrder o, boolean fullyRefunded) {
        if (fullyRefunded) { o.setStatus(OrderStatus.REFUNDED); return; }
        var s = shipments.findByOrderId(o.getId());
        o.setStatus(s.isPresent() ? (s.get().getStatus() == ShipmentStatus.DELIVERED ? OrderStatus.DELIVERED : OrderStatus.SHIPPED) : OrderStatus.PAID);
    }

    // ---- reads
    @Transactional(readOnly = true)
    public List<DisputeDto> mine(AuthUser me) { return disputes.findByRaisedByOrAgainstUserOrderByCreatedAtDesc(me.id(), me.id()).stream().map(this::dto).toList(); }

    @Transactional(readOnly = true)
    public List<DisputeDto> queue(DisputeStatus status, DisputePriority priority) {
        return disputes.findTop500ByOrderByCreatedAtDesc().stream().filter(d -> status == null || d.getStatus() == status).filter(d -> priority == null || d.getPriority() == priority)
                .sorted(Comparator.comparing((Dispute d) -> FINAL.contains(d.getStatus())).thenComparing(Comparator.comparing((Dispute d) -> d.getPriority().ordinal()).reversed()).thenComparing(Dispute::getCreatedAt))
                .map(this::dto).toList();
    }
    @Transactional(readOnly = true)
    public List<DisputeDto> history(AuthUser me) {
        return resolutions.findByResolverIdOrderByCreatedAtDesc(me.id()).stream().map(r -> dto(load(r.getDisputeId()))).toList();
    }

    @Transactional(readOnly = true)
    public DisputeDetailDto detail(AuthUser me, Long id) {
        Dispute d = load(id); requireView(me, d);
        boolean staff = me.isStaff() && !party(me, d);
        PurchaseOrder o = orders.findById(d.getOrderId()).orElseThrow();
        Listing l = listings.findById(o.getListingId()).orElseThrow();
        List<MessageDto> msgs = messages.findByDisputeIdOrderByCreatedAtAsc(id).stream().filter(m -> staff || m.getVisibility() != MessageVisibility.INTERNAL).map(this::msgDto).toList();
        Map<Long, String> roleOf = new HashMap<>(); roleOf.put(o.getBuyerId(), "BUYER"); roleOf.put(o.getSellerId(), "SELLER");
        List<EvidenceDto> evs = evidence.findByDisputeIdOrderByCreatedAtAsc(id).stream().map(this::evDto).toList();
        ResolutionDto res = resolutions.findByDisputeId(id).map(x -> new ResolutionDto(x.getResolutionType(), x.getRefundAmount(), x.getDecisionText(), lookup.name(x.getResolverId()), x.getCreatedAt())).orElse(null);
        var pay = payments.findFirstByOrderIdOrderByIdDesc(o.getId()); var shp = shipments.findByOrderId(o.getId());
        OrderSummary os = new OrderSummary(o.getId(), l.getName(), o.getAmount(), o.getStatus(), pay.map(Payment::getStatus).orElse(null), shp.map(Shipment::getStatus).orElse(null));
        List<String> hist = assignments.findByDisputeIdOrderByIdAsc(id).stream().map(a -> a.getCreatedAt() + ": assigned to " + lookup.name(a.getResolverId()) + " by " + lookup.name(a.getAssignedBy())).toList();
        return new DisputeDetailDto(dto(d), evs, msgs, res, partyDto(o.getBuyerId(), me), partyDto(o.getSellerId(), me), os, hist);
    }

    /** Resolvers see names and city only; contact details are visible to admins, and parties see the counterpart's name. */
    private PartyDto partyDto(Long uid, AuthUser viewer) {
        AppUser u = lookup.user(uid);
        String city = sellers.findByUserId(uid).map(SellerProfile::getCity).orElseGet(() -> buyers.findByUserId(uid).map(BuyerProfile::getCity).orElse(null));
        return new PartyDto(uid, u.getFullName(), city, viewer.has(Role.RESOLVER) ? u.getEmail() : null);
    }

    private DisputeDto dto(Dispute d) {
        PurchaseOrder o = orders.findById(d.getOrderId()).orElseThrow();
        return new DisputeDto(d.getId(), d.getOrderId(), listings.findById(o.getListingId()).map(Listing::getName).orElse("?"), d.getRaisedBy(), lookup.name(d.getRaisedBy()),
                d.getAgainstUser(), lookup.name(d.getAgainstUser()), d.getResolverId(), lookup.name(d.getResolverId()), d.getCategory(), d.getStatus(), d.getPriority(),
                d.getDescription(), o.getAmount(), d.getCreatedAt(), d.getResolvedAt());
    }
    private EvidenceDto evDto(DisputeEvidence e) {
        AppUser u = lookup.user(e.getSubmittedBy());
        return new EvidenceDto(e.getId(), e.getSubmittedBy(), u.getFullName(), u.roleSet().stream().filter(x -> x == Role.BUYER || x == Role.SELLER).map(Enum::name).findFirst().orElse("USER"),
                e.getCategory(), e.getDescription(), e.getOriginalName(), e.getContentType(), e.getSha256(), e.getCreatedAt());
    }
    private MessageDto msgDto(DisputeMessage m) { return new MessageDto(m.getId(), m.getSenderId(), lookup.name(m.getSenderId()), m.getVisibility(), m.getBody(), m.getCreatedAt()); }
}
