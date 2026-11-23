package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.engine.HazardEngine;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Feature C: questionnaire-based hazard assessment, shipping block / specialist routing, and verified data-sanitization certificates. */
@Service @RequiredArgsConstructor
public class HazardService {
    private final HazardRuleRepository rules; private final HazardInspectionRepository inspections; private final SanitizationRecordRepository sanitizations;
    private final ListingRepository listings; private final PurchaseOrderRepository orders; private final Lookup lookup; private final AuditService audit;
    private final NotificationService notifications; private final LifecycleService lifecycle; private final ScoringService scoring; private final PassportService passports;
    private final ObjectMapper json;
    private final HazardEngine engine = new HazardEngine();

    public HazardEngine.Result evaluate(Map<String, Boolean> answers) {
        var rs = rules.findByEnabledTrue().stream().map(r -> new HazardEngine.Rule(r.getCode(), r.getDescription(), r.getPoints(),
                r.isBlocksShipping(), r.isRequiresSanitization(), r.getHandling(), r.getSpecialistType())).toList();
        if (answers != null) {
            Set<String> known = new HashSet<>(); rs.forEach(r -> known.add(r.code()));
            for (String k : answers.keySet()) if (!known.contains(k)) throw ApiException.badRequest("Unknown hazard question: " + k);
        }
        return engine.assess(rs, answers);
    }

    /** Copies the assessment outcome onto the listing (level, shipping block, specialist routing, sanitization requirement). */
    public void apply(Listing l, HazardEngine.Result r) {
        l.setHazardLevel(r.level()); l.setShippingBlocked(r.blocked());
        l.setSpecialistRequired(r.blocked() || r.level().ordinal() >= HazardLevel.HIGH.ordinal() ? String.join("; ", r.specialists()) : null);
        l.setRequiresSanitization(r.requiresSanitization());
        if (!r.requiresSanitization()) l.setSanitizationVerified(false);
    }

    @Transactional
    public HazardInspection record(Listing l, Map<String, Boolean> answers, HazardEngine.Result r, Long inspectorId) {
        HazardInspection h = new HazardInspection();
        h.setListingId(l.getId()); h.setInspectorId(inspectorId); h.setTotalPoints(r.totalPoints()); h.setLevel(r.level()); h.setBlocked(r.blocked());
        try { h.setAnswers(json.writeValueAsString(answers == null ? Map.of() : answers)); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        h.setExplanation(String.join("\n", r.findings())); h.setHandling(String.join("\n", r.handling())); h.setSpecialist(String.join("; ", r.specialists()));
        inspections.save(h);
        audit.log(inspectorId, "HAZARD_ASSESSED", "listings", l.getId(), "level=" + r.level() + " points=" + r.totalPoints() + " blocked=" + r.blocked());
        if (r.blocked()) lifecycle.record(l.getId(), LifecycleStage.HAZARD_HOLD, l.getPathway(), inspectorId, "Normal shipping blocked: " + String.join("; ", r.specialists()));
        return h;
    }

    @Transactional
    public HazardDto reassess(AuthUser me, Long listingId, Map<String, Boolean> answers) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (!me.has(Role.RESOLVER) && !l.getSellerId().equals(me.id())) throw ApiException.forbidden("Only the seller or an admin can re-assess this product");
        if (l.getStatus() == ListingStatus.SOLD || l.getStatus() == ListingStatus.RESERVED) throw ApiException.conflict("A reserved or sold product cannot be re-assessed");
        var r = evaluate(answers);
        apply(l, r); scoring.apply(l);
        if (r.level().ordinal() >= HazardLevel.HIGH.ordinal() && (l.getPathway() == Pathway.REUSE || l.getPathway() == Pathway.REPAIR || l.getPathway() == Pathway.REFURBISH)) l.setPathway(l.getRecommendedPathway());
        var h = record(l, answers, r, me.id());
        return toDto(h, me);
    }

    @Transactional(readOnly = true)
    public List<HazardDto> history(AuthUser me, Long listingId) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (!me.has(Role.RESOLVER) && !l.getSellerId().equals(me.id())) throw ApiException.forbidden("Inspection history is visible to the seller and admins");
        return inspections.findByListingIdOrderByIdDesc(listingId).stream().map(h -> toDto(h, me)).toList();
    }

    private HazardDto toDto(HazardInspection h, AuthUser viewer) {
        return new HazardDto(h.getId(), h.getListingId(), h.getLevel(), h.getTotalPoints(), h.isBlocked(), h.getExplanation() != null && listings.findById(h.getListingId()).map(Listing::isRequiresSanitization).orElse(false),
                h.getSpecialist(), List.of(h.getExplanation().split("\n")), h.getHandling() == null || h.getHandling().isBlank() ? List.of() : List.of(h.getHandling().split("\n")),
                lookup.label(h.getInspectorId(), viewer), h.getCreatedAt());
    }

    // ---- rules (admin)
    @Transactional(readOnly = true)
    public List<HazardRuleDto> listRules() { return rules.findAll().stream().sorted(Comparator.comparing(HazardRule::getCode)).map(HazardService::ruleDto).toList(); }
    @Transactional
    public HazardRuleDto saveRule(Long adminId, Long id, HazardRuleRequest r) {
        HazardRule h = id == null ? new HazardRule() : rules.findById(id).orElseThrow(() -> ApiException.notFound("Rule not found"));
        if (id == null && rules.findByCode(r.code()).isPresent()) throw ApiException.conflict("A rule with this code already exists");
        h.setCode(id == null ? r.code().trim().toUpperCase() : h.getCode()); h.setDescription(r.description()); h.setPoints(r.points());
        h.setBlocksShipping(r.blocksShipping()); h.setRequiresSanitization(r.requiresSanitization()); h.setHandling(r.handling());
        h.setSpecialistType(r.specialistType()); h.setEnabled(r.enabled());
        rules.save(h);
        audit.log(adminId, id == null ? "HAZARD_RULE_CREATED" : "HAZARD_RULE_UPDATED", "hazard_rules", h.getId(), h.getCode() + " points=" + h.getPoints() + " enabled=" + h.isEnabled());
        return ruleDto(h);
    }
    private static HazardRuleDto ruleDto(HazardRule h) {
        return new HazardRuleDto(h.getId(), h.getCode(), h.getDescription(), h.getPoints(), h.isBlocksShipping(), h.isRequiresSanitization(), h.getHandling(), h.getSpecialistType(), h.isEnabled());
    }

    // ---- data sanitization
    @Transactional
    public SanitizationDto submit(AuthUser me, Long listingId, SanitizationRequest r) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (!l.getSellerId().equals(me.id())) throw ApiException.forbidden("Only the seller can submit sanitization evidence");
        if (!l.isRequiresSanitization()) throw ApiException.conflict("This product does not require data sanitization");
        if (l.isSanitizationVerified()) throw ApiException.conflict("Sanitization is already verified");
        if (sanitizations.findByListingIdOrderByIdDesc(listingId).stream().anyMatch(s -> s.getStatus() == SanitizationStatus.SUBMITTED)) throw ApiException.conflict("A submission is already awaiting verification");
        SanitizationRecord s = new SanitizationRecord();
        s.setListingId(listingId); s.setSubmittedBy(me.id()); s.setMethod(r.method()); s.setToolName(r.toolName()); s.setEvidenceRef(r.evidenceRef());
        sanitizations.save(s);
        audit.log(me.id(), "SANITIZATION_SUBMITTED", "listings", listingId, r.method() + " / " + r.toolName());
        notifications.notifyRole(Role.RESOLVER, "Sanitization awaiting verification", "Listing #" + listingId + " (" + l.getName() + ") has erase evidence to review.", "SANITIZATION");
        return toDto(s, l.getName());
    }

    /** An admin attests that the submitted erase report is genuine. The certificate exists only after this approval; it is a human attestation, not an automatic hardware check. */
    @Transactional
    public SanitizationDto verify(Long adminId, Long recordId, VerifyRequest r) {
        SanitizationRecord s = sanitizations.findById(recordId).orElseThrow(() -> ApiException.notFound("Record not found"));
        if (s.getStatus() != SanitizationStatus.SUBMITTED) throw ApiException.conflict("This submission was already reviewed");
        if (s.getSubmittedBy().equals(adminId)) throw ApiException.forbidden("You cannot verify your own submission");
        Listing l = listings.findById(s.getListingId()).orElseThrow();
        s.setVerifiedBy(adminId); s.setVerifiedAt(java.time.Instant.now()); s.setNote(r.note());
        if (r.approve()) {
            s.setStatus(SanitizationStatus.VERIFIED);
            s.setCertificateNo("DSC-" + LocalDate.now(ZoneOffset.UTC).toString().replace("-", "") + "-" + String.format("%06d", s.getId()));
            l.setSanitizationVerified(true);
            passports.event(l.getId(), "SANITIZED", adminId, "Data sanitization verified, certificate " + s.getCertificateNo());
        } else s.setStatus(SanitizationStatus.REJECTED);
        audit.log(adminId, r.approve() ? "SANITIZATION_VERIFIED" : "SANITIZATION_REJECTED", "listings", l.getId(), s.getCertificateNo());
        notifications.notify(l.getSellerId(), r.approve() ? "Sanitization verified" : "Sanitization rejected",
                r.approve() ? "Certificate " + s.getCertificateNo() + " issued for " + l.getName() : "Evidence was rejected: " + (r.note() == null ? "no reason given" : r.note()), "SANITIZATION");
        return toDto(s, l.getName());
    }

    @Transactional(readOnly = true)
    public List<SanitizationDto> pending() {
        return sanitizations.findByStatusOrderByIdAsc(SanitizationStatus.SUBMITTED).stream().map(s -> toDto(s, listings.findById(s.getListingId()).map(Listing::getName).orElse("?"))).toList();
    }
    @Transactional(readOnly = true)
    public List<SanitizationDto> forListing(AuthUser me, Long listingId) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing not found"));
        boolean buyer = orders.findFirstByListingIdOrderByIdDesc(listingId).map(o -> o.getBuyerId().equals(me.id())).orElse(false);
        if (!me.has(Role.RESOLVER) && !l.getSellerId().equals(me.id()) && !buyer) throw ApiException.forbidden("Not allowed");
        return sanitizations.findByListingIdOrderByIdDesc(listingId).stream().map(s -> toDto(s, l.getName())).toList();
    }
    /** Returns the certificate only if a VERIFIED record exists. */
    @Transactional(readOnly = true)
    public SanitizationDto certificate(AuthUser me, Long listingId) {
        return forListing(me, listingId).stream().filter(s -> s.status() == SanitizationStatus.VERIFIED).findFirst()
                .orElseThrow(() -> ApiException.notFound("No verified sanitization certificate exists for this product"));
    }
    private SanitizationDto toDto(SanitizationRecord s, String listingName) {
        return new SanitizationDto(s.getId(), s.getListingId(), listingName, s.getMethod(), s.getToolName(), s.getEvidenceRef(), s.getStatus(),
                lookup.name(s.getSubmittedBy()), lookup.name(s.getVerifiedBy()), s.getVerifiedAt(), s.getCertificateNo(), s.getNote(), s.getCreatedAt());
    }
}
