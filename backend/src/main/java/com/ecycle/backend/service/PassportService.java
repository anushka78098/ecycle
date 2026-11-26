package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Feature A: digital e-waste passport, QR codes, ownership/service history and component genealogy. */
@Service @RequiredArgsConstructor
public class PassportService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Set<String> COMPONENT_ACTIONS = Set.of("RESELL", "RECOVER", "REUSE", "REPAIR", "REFURBISH");
    private final PassportRepository passports; private final PassportEventRepository events; private final ListingRepository listings;
    private final ProductCategoryRepository categories; private final UserRepository users; private final BuyerProfileRepository buyers;
    private final LifecycleService lifecycle; private final Lookup lookup; private final AuditService audit; private final NotificationService notifications;
    private final SecureRandom rnd = new SecureRandom();
    /** Optional collaborator (setter-injected so the constructor is unchanged): mirrors every passport event into the tamper-evident custody chain. */
    private CustodyService custody;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setCustody(CustodyService c) { this.custody = c; }

    public static String qrPayload(String code) { return "ecycle://passport/" + code; }

    @Transactional
    public Passport createFor(Listing l, Long actorId) {
        Passport p = new Passport();
        String code; do { StringBuilder sb = new StringBuilder("ECP-"); for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length()))); code = sb.toString(); }
        while (passports.findByPassportCode(code).isPresent());
        p.setPassportCode(code); p.setListingId(l.getId()); p.setName(l.getName()); p.setComponentType(lookup.category(l.getCategoryId()).getName());
        p.setStage(LifecycleStage.LISTED); p.setCurrentOwnerId(l.getSellerId());
        passports.save(p);
        addEvent(p, "CREATED", actorId, "Passport issued when the product was listed", null);
        return p;
    }

    private PassportEvent addEvent(Passport p, String type, Long actorId, String detail, BigDecimal value) {
        PassportEvent e = new PassportEvent();
        e.setPassportId(p.getId()); e.setEventType(type); e.setActorId(actorId); e.setDetail(detail); e.setValueAmount(value);
        PassportEvent saved = events.save(e);
        if (custody != null) custody.mirrorPassportEvent(p, type, actorId, detail);
        return saved;
    }
    /** Adds an event to the root passport of a listing (no-op if the listing has none). */
    @Transactional
    public void event(Long listingId, String type, Long actorId, String detail) {
        passports.findByListingId(listingId).ifPresent(p -> addEvent(p, type, actorId, detail, null));
    }
    @Transactional
    public void onApproved(Listing l, Long adminId) {
        passports.findByListingId(l.getId()).ifPresent(p -> { p.setStage(LifecycleStage.APPROVED); addEvent(p, "APPROVED", adminId, "Listing approved by moderation", null); });
    }
    @Transactional
    public void onPurchased(Listing l, Long buyerId, BigDecimal amount) {
        passports.findByListingId(l.getId()).ifPresent(p -> { p.setStage(LifecycleStage.PURCHASED); addEvent(p, "PURCHASED", buyerId, "Payment verified; sale price recorded", amount); });
    }
    /** Ownership moves to the buyer on confirmed delivery. */
    @Transactional
    public void onDelivered(Listing l, Long buyerId) {
        passports.findByListingId(l.getId()).ifPresent(p -> {
            Long from = p.getCurrentOwnerId(); p.setCurrentOwnerId(buyerId); p.setStage(LifecycleStage.DELIVERED);
            addEvent(p, "OWNERSHIP_TRANSFERRED", buyerId, "Delivery confirmed; ownership moved from user #" + from + " to user #" + buyerId, null);
        });
    }

    @Transactional(readOnly = true)
    public PassportDto view(AuthUser me, String code) { return tree(find(code), me); }
    @Transactional(readOnly = true)
    public PassportDto forListing(AuthUser me, Long listingId) {
        return tree(passports.findByListingId(listingId).orElseThrow(() -> ApiException.notFound("No passport for this listing")), me);
    }
    @Transactional(readOnly = true)
    public List<PassportDto> mine(AuthUser me) {
        return passports.findByCurrentOwnerIdOrderByCreatedAtDesc(me.id()).stream().filter(p -> p.getParentId() == null).map(p -> tree(p, me)).toList();
    }
    private Passport find(String code) { return passports.findByPassportCode(code).orElseThrow(() -> ApiException.notFound("Passport not found")); }

    private PassportDto tree(Passport p, AuthUser viewer) {
        String parentCode = p.getParentId() == null ? null : passports.findById(p.getParentId()).map(Passport::getPassportCode).orElse(null);
        List<PassportDto> kids = passports.findByParentIdOrderByIdAsc(p.getId()).stream().map(c -> tree(c, viewer)).toList();
        List<PassportEventDto> evs = events.findByPassportIdOrderByIdAsc(p.getId()).stream()
                .map(e -> new PassportEventDto(e.getEventType(), lookup.label(e.getActorId(), viewer), e.getDetail(), e.getValueAmount(), e.getCreatedAt())).toList();
        return new PassportDto(p.getPassportCode(), p.getListingId(), p.getName(), p.getComponentType(), p.getStage(), lookup.label(p.getCurrentOwnerId(), viewer),
                parentCode, qrPayload(p.getPassportCode()), p.getCreatedAt(), kids, evs);
    }

    public byte[] qrPng(String code, int size) {
        find(code);
        int s = Math.max(120, Math.min(800, size));
        try {
            BitMatrix m = new QRCodeWriter().encode(qrPayload(code), BarcodeFormat.QR_CODE, s, s, Map.of(EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream(); MatrixToImageWriter.writeToStream(m, "PNG", out); return out.toByteArray();
        } catch (WriterException | IOException e) { throw new IllegalStateException("QR generation failed", e); }
    }

    private void requireOwner(AuthUser me, Passport p) {
        if (!me.has(Role.RESOLVER) && !me.id().equals(p.getCurrentOwnerId())) throw ApiException.forbidden("Only the current owner or an admin can change this passport");
    }

    /** Splits a delivered product into component passports linked to the parent. Hazardous products may only be dismantled by verified specialist recyclers. */
    @Transactional
    public PassportDto dismantle(AuthUser me, String code, DismantleRequest r) {
        Passport p = find(code); requireOwner(me, p);
        if (p.getListingId() == null) throw ApiException.conflict("Components cannot be dismantled further");
        Listing l = listings.findById(p.getListingId()).orElseThrow();
        if (!categories.findById(l.getCategoryId()).map(ProductCategory::isDismantlable).orElse(false)) throw ApiException.conflict("This product category cannot be dismantled into tracked components");
        if (p.getStage() != LifecycleStage.DELIVERED && !me.has(Role.RESOLVER)) throw ApiException.conflict("Only a delivered product can be dismantled");
        if (p.getStage() == LifecycleStage.DISMANTLED) throw ApiException.conflict("Already dismantled");
        if (l.isShippingBlocked() && !me.has(Role.RESOLVER)) {
            boolean specialist = buyers.findByUserId(me.id()).map(b -> b.isVerified() && b.getBuyerType() == BuyerType.RECYCLER).orElse(false);
            if (!specialist) throw ApiException.forbidden("Hazardous products must be dismantled by a verified specialist recycler");
        }
        if (r.components().size() > 12) throw ApiException.badRequest("At most 12 components per dismantling");
        for (ComponentSpec c : r.components()) {
            Passport child = new Passport(); String cc;
            do { StringBuilder sb = new StringBuilder("ECC-"); for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length()))); cc = sb.toString(); }
            while (passports.findByPassportCode(cc).isPresent());
            child.setPassportCode(cc); child.setParentId(p.getId()); child.setName(c.name()); child.setComponentType(c.componentType());
            child.setStage(LifecycleStage.COMPONENT_RECOVERED); child.setCurrentOwnerId(p.getCurrentOwnerId()); passports.save(child);
            addEvent(child, "CREATED_FROM_PARENT", me.id(), "Recovered from " + p.getPassportCode(), null);
        }
        p.setStage(LifecycleStage.DISMANTLED);
        addEvent(p, "DISMANTLED", me.id(), "Dismantled into " + r.components().size() + " tracked components", null);
        lifecycle.record(l.getId(), LifecycleStage.DISMANTLED, l.getPathway(), me.id(), "Dismantled into " + r.components().size() + " components");
        audit.log(me.id(), "PASSPORT_DISMANTLED", "passports", p.getId(), p.getPassportCode());
        return tree(p, me);
    }

    @Transactional
    public PassportDto componentAction(AuthUser me, String code, ComponentActionRequest r) {
        Passport c = find(code); requireOwner(me, c);
        if (c.getParentId() == null) throw ApiException.badRequest("Use the outcome endpoint for whole products");
        String action = r.action().toUpperCase(Locale.ROOT);
        if (!COMPONENT_ACTIONS.contains(action)) throw ApiException.badRequest("Unknown action; use one of " + COMPONENT_ACTIONS);
        switch (action) {
            case "RESELL" -> {
                if (r.toUserId() == null || r.amount() == null) throw ApiException.badRequest("RESELL needs toUserId and amount");
                AppUser to = users.findById(r.toUserId()).orElseThrow(() -> ApiException.notFound("Recipient not found"));
                if (!to.hasRole(Role.BUYER) || to.getId().equals(me.id())) throw ApiException.badRequest("Recipient must be a different buyer account");
                c.setCurrentOwnerId(to.getId()); c.setStage(LifecycleStage.REUSED);
                addEvent(c, "COMPONENT_RESOLD", me.id(), "Sold to user #" + to.getId() + (r.note() == null ? "" : ": " + r.note()), r.amount());
                notifications.notify(to.getId(), "Component transferred to you", c.getName() + " (" + c.getPassportCode() + ")", "PASSPORT");
            }
            case "RECOVER" -> { c.setStage(LifecycleStage.RECYCLED); addEvent(c, "COMPONENT_RECOVERED", me.id(), r.note(), r.amount()); }
            case "REUSE" -> { c.setStage(LifecycleStage.REUSED); addEvent(c, "COMPONENT_REUSED", me.id(), r.note(), r.amount()); }
            case "REPAIR" -> { c.setStage(LifecycleStage.REPAIRED); addEvent(c, "COMPONENT_REPAIRED", me.id(), r.note(), r.amount()); }
            default -> { c.setStage(LifecycleStage.REFURBISHED); addEvent(c, "COMPONENT_REFURBISHED", me.id(), r.note(), r.amount()); }
        }
        audit.log(me.id(), "COMPONENT_" + action, "passports", c.getId(), c.getPassportCode());
        return tree(c, me);
    }

    /** Records a repair / refurbishment / reuse / recycling outcome for a delivered whole product and its (estimated or measured) environmental impact. */
    @Transactional
    public PassportDto outcome(AuthUser me, String code, OutcomeRequest r) {
        Passport p = find(code); requireOwner(me, p);
        if (p.getListingId() == null) throw ApiException.badRequest("Use component actions for components");
        if (p.getStage() == LifecycleStage.LISTED || p.getStage() == LifecycleStage.APPROVED || p.getStage() == LifecycleStage.PURCHASED)
            throw ApiException.conflict("Outcomes can only be recorded after delivery");
        Listing l = listings.findById(p.getListingId()).orElseThrow();
        BigDecimal measured = r.measuredWeightKg();
        if (measured != null) {
            boolean specialist = me.has(Role.RESOLVER) || buyers.findByUserId(me.id()).map(b -> b.isVerified() && b.getBuyerType() == BuyerType.RECYCLER).orElse(false);
            if (!specialist) throw ApiException.forbidden("Only verified specialist recyclers or admins may submit measured weights");
        }
        LifecycleStage stage = lifecycle.recordOutcome(l, r.pathway(), me.id(), r.note(), measured);
        p.setStage(stage);
        addEvent(p, stage.name(), me.id(), r.note(), null);
        audit.log(me.id(), "OUTCOME_" + stage, "passports", p.getId(), p.getPassportCode());
        return tree(p, me);
    }
}
