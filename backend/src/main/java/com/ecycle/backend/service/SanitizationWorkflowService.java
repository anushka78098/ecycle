package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.engine.WipeChecklists;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data-wipe checklist workflow layered on the existing sanitization module: the seller completes a category-specific checklist, which creates the same SanitizationRecord the admin
 * already verifies (HazardService); verification then issues the DSC number and the PDF certificate.
 */
@Service @RequiredArgsConstructor
public class SanitizationWorkflowService {
    private final ListingRepository listings; private final SanitizationChecklistRepository checklists; private final HazardService hazard; private final Lookup lookup;
    private final CustodyService custody; private final AuditService audit;

    @Transactional(readOnly = true)
    public ChecklistTemplateDto template(Long listingId) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing not found"));
        return templateFor(l.getId(), l.getCategoryId());
    }
    public ChecklistTemplateDto templateFor(Long listingId, Long categoryId) {
        String cat = categoryId == null ? null : lookup.category(categoryId).getName();
        return new ChecklistTemplateDto(listingId, cat, WipeChecklists.forCategory(cat).stream().map(i -> new ChecklistItemDto(i.code(), i.text(), i.mandatory())).toList(), WipeChecklists.METHODS);
    }

    /** Validates a checklist against the category template. Throws 400 with the reason when unknown items, an unknown method or missing mandatory steps are submitted. */
    public void validate(Long categoryId, String method, Collection<String> done) {
        String cat = categoryId == null ? null : lookup.category(categoryId).getName();
        if (!WipeChecklists.knownMethod(method)) throw ApiException.badRequest("Unknown sanitization method. Choose one of: " + String.join(", ", WipeChecklists.METHODS));
        List<String> unknown = WipeChecklists.unknownCodes(cat, done);
        if (!unknown.isEmpty()) throw ApiException.badRequest("Unknown checklist items: " + unknown);
        List<String> missing = WipeChecklists.missingMandatory(cat, done);
        if (!missing.isEmpty()) throw ApiException.badRequest("Mandatory checklist steps not completed: " + missing);
    }

    /** Seller path: checklist + evidence -> pending record for admin verification. */
    @Transactional
    public ChecklistDto submitForListing(AuthUser me, Long listingId, ChecklistSubmitRequest r) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (!l.getSellerId().equals(me.id())) throw ApiException.forbidden("Only the seller can submit the wipe checklist");
        List<String> done = r.completedItems().stream().distinct().toList();
        validate(l.getCategoryId(), r.method(), done);
        SanitizationDto rec = hazard.submit(me, listingId, new SanitizationRequest(r.method(), r.toolName(), r.evidenceRef()));
        SanitizationChecklist c = new SanitizationChecklist();
        c.setListingId(listingId); c.setRecordId(rec.id()); c.setCompletedBy(me.id()); c.setCompletedRole("SELLER"); c.setMethod(r.method()); c.setDeviceSerial(r.deviceSerial());
        c.setItems(String.join(",", done)); c.setAllMandatoryDone(true);
        checklists.save(c);
        audit.log(me.id(), "WIPE_CHECKLIST_COMPLETED", "listings", listingId, r.method() + ", " + done.size() + " steps");
        String code = custody.passportCodeForListing(listingId);
        if (code != null) { try { custody.recordSystem(code, "SANITIZATION_SUBMITTED", me.id(), null, null, r.method() + " checklist completed"); } catch (RuntimeException ignored) { /* chain is best effort here */ } }
        return dto(c);
    }

    /** Recycler path (used by PickupService.verify): stores the checklist against the pickup. */
    @Transactional
    public SanitizationChecklist recordForPickup(AuthUser me, Pickup p, String method, String serial, List<String> done) {
        List<String> items = done == null ? List.of() : done.stream().distinct().toList();
        validate(p.getCategoryId(), method, items);
        SanitizationChecklist c = new SanitizationChecklist();
        c.setListingId(p.getListingId()); c.setPickupId(p.getId()); c.setCompletedBy(me.id()); c.setCompletedRole("RECYCLER"); c.setMethod(method); c.setDeviceSerial(serial);
        c.setItems(String.join(",", items)); c.setAllMandatoryDone(true);
        return checklists.save(c);
    }

    @Transactional(readOnly = true)
    public List<ChecklistDto> forListing(AuthUser me, Long listingId) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (!me.has(Role.RESOLVER) && !l.getSellerId().equals(me.id())) throw ApiException.notFound("Listing not found");
        return checklists.findByListingIdOrderByIdDesc(listingId).stream().map(this::dto).toList();
    }

    private ChecklistDto dto(SanitizationChecklist c) {
        List<String> items = c.getItems() == null || c.getItems().isBlank() ? List.of() : Arrays.asList(c.getItems().split(","));
        return new ChecklistDto(c.getId(), c.getListingId(), c.getRecordId(), c.getPickupId(), lookup.name(c.getCompletedBy()), c.getCompletedRole(), c.getMethod(), c.getDeviceSerial(), items, c.isAllMandatoryDone(), c.getCreatedAt());
    }
}
