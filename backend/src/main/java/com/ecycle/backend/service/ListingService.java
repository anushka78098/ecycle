package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service @RequiredArgsConstructor
public class ListingService {
    static final int MAX_IMAGES = 8;
    private final ListingRepository repo; private final ListingImageRepository images; private final SellerProfileRepository sellers;
    private final Lookup lookup; private final ScoringService scoring; private final HazardService hazard; private final LifecycleService lifecycle;
    private final PassportService passports; private final AuctionService auctions; private final ListingMapper mapper; private final FileStorageService storage;
    private final AuditService audit; private final NotificationService notifications; private final PurchaseOrderRepository orders;

    public record SearchFilter(String q, Long categoryId, String brand, ItemCondition condition, BigDecimal minPrice, BigDecimal maxPrice,
                               String location, FunctionalStatus functional, Pathway pathway, String sort, int page, int size) {}

    private static boolean riskyPathway(Pathway p) { return p == Pathway.REUSE || p == Pathway.REPAIR || p == Pathway.REFURBISH; }

    private void fill(Listing l, ListingRequest r) {
        l.setName(r.name().trim()); l.setBrand(r.brand()); l.setModel(r.model()); l.setCategoryId(r.categoryId()); l.setAgeMonths(r.ageMonths());
        l.setPhysicalCondition(r.physicalCondition()); l.setFunctionalStatus(r.functionalStatus()); l.setQuantity(r.quantity()); l.setExpectedPrice(r.expectedPrice());
        l.setDescription(r.description()); l.setPickupCity(r.pickupCity().trim()); l.setPickupAddress(r.pickupAddress()); l.setWeightKg(r.weightKg());
        l.setAuctionEnabled(r.auctionEnabled());
        if (r.auctionEnabled()) {
            int h = r.auctionHours() == null ? 72 : r.auctionHours();
            if (h < 1 || h > 720) throw ApiException.badRequest("Auction duration must be 1 to 720 hours");
            l.setAuctionHours(h); l.setAuctionMinPrice(r.auctionMinPrice() == null ? BigDecimal.ZERO : r.auctionMinPrice());
        } else { l.setAuctionHours(null); l.setAuctionMinPrice(null); }
    }

    /** Hazard first (it influences scoring), then score, then validate the seller's chosen pathway against the hazard level. */
    private com.ecycle.backend.engine.HazardEngine.Result assessAndScore(Listing l, ListingRequest r) {
        var hz = hazard.evaluate(r.hazardAnswers());
        hazard.apply(l, hz); scoring.apply(l);
        Pathway chosen = r.pathway() == null ? l.getRecommendedPathway() : r.pathway();
        if (l.getHazardLevel().ordinal() >= HazardLevel.HIGH.ordinal() && riskyPathway(chosen))
            throw ApiException.badRequest("Hazardous items (level " + l.getHazardLevel() + ") can only be routed to RECYCLE or DISPOSE");
        l.setPathway(chosen);
        return hz;
    }

    @Transactional
    public ListingDto create(AuthUser me, ListingRequest r) {
        lookup.requireRole(me, Role.SELLER);
        sellers.findByUserId(me.id()).filter(s -> s.getVerificationStatus() == VerificationStatus.REJECTED).ifPresent(s -> { throw ApiException.forbidden("Your seller verification was rejected; contact support"); });
        if (!lookup.category(r.categoryId()).isActive()) throw ApiException.badRequest("This category is not active");
        Listing l = new Listing(); fill(l, r); l.setSellerId(me.id()); l.setStatus(ListingStatus.PENDING_APPROVAL);
        var hz = assessAndScore(l, r);
        repo.save(l);
        hazard.record(l, r.hazardAnswers(), hz, me.id());
        lifecycle.record(l.getId(), LifecycleStage.LISTED, l.getPathway(), me.id(), "Listing submitted; Circular Value Score " + l.getCircularScore());
        passports.createFor(l, me.id());
        audit.log(me.id(), "LISTING_CREATED", "listings", l.getId(), l.getName());
        notifications.notifyRole(Role.RESOLVER, "Listing awaiting approval", "\"" + l.getName() + "\" was submitted by seller #" + me.id(), "LISTING");
        return mapper.toDto(l);
    }

    private Listing own(AuthUser me, Long id) {
        Listing l = repo.findById(id).orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (!l.getSellerId().equals(me.id())) throw ApiException.forbidden("This is not your listing");
        return l;
    }
    private static void requireEditable(Listing l) {
        if (l.getStatus() != ListingStatus.PENDING_APPROVAL && l.getStatus() != ListingStatus.REJECTED) throw ApiException.conflict("Only pending or rejected listings can be edited or withdrawn; current status: " + l.getStatus());
    }

    @Transactional
    public ListingDto update(AuthUser me, Long id, ListingRequest r) {
        Listing l = own(me, id); requireEditable(l);
        if (!lookup.category(r.categoryId()).isActive()) throw ApiException.badRequest("This category is not active");
        fill(l, r);
        var hz = assessAndScore(l, r);
        l.setStatus(ListingStatus.PENDING_APPROVAL); l.setRejectionReason(null);
        hazard.record(l, r.hazardAnswers(), hz, me.id());
        audit.log(me.id(), "LISTING_UPDATED", "listings", id, l.getName());
        return mapper.toDto(l);
    }

    /** "Delete" is a soft withdrawal so lifecycle, passport and audit history remain intact. */
    @Transactional
    public void withdraw(AuthUser me, Long id) {
        Listing l = own(me, id); requireEditable(l);
        if (orders.existsByListingId(id)) throw ApiException.conflict("A listing with orders cannot be withdrawn");
        l.setStatus(ListingStatus.WITHDRAWN);
        audit.log(me.id(), "LISTING_WITHDRAWN", "listings", id, l.getName());
    }

    @Transactional
    public List<Long> uploadImages(AuthUser me, Long id, List<MultipartFile> files) {
        Listing l = own(me, id);
        if (l.getStatus() == ListingStatus.SOLD || l.getStatus() == ListingStatus.WITHDRAWN) throw ApiException.conflict("Images cannot be added to this listing");
        if (files == null || files.isEmpty()) throw ApiException.badRequest("No files uploaded");
        if (images.countByListingId(id) + files.size() > MAX_IMAGES) throw ApiException.badRequest("A listing can have at most " + MAX_IMAGES + " images");
        List<Long> out = new ArrayList<>();
        for (MultipartFile f : files) {
            var s = storage.store(f, "listings/" + id, FileStorageService.IMAGE_TYPES, 5L * 1024 * 1024);
            ListingImage i = new ListingImage(); i.setListingId(id); i.setFilePath(s.path()); i.setOriginalName(s.originalName()); i.setContentType(s.contentType()); i.setSha256(s.sha256());
            out.add(images.save(i).getId());
        }
        audit.log(me.id(), "LISTING_IMAGES_ADDED", "listings", id, out.size() + " image(s)");
        return out;
    }

    public record ImageFile(Resource resource, String contentType) {}
    @Transactional(readOnly = true)
    public ImageFile image(AuthUser me, Long listingId, Long imageId) {
        Listing l = repo.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing not found"));
        boolean publicStatus = l.getStatus() == ListingStatus.APPROVED || l.getStatus() == ListingStatus.RESERVED || l.getStatus() == ListingStatus.SOLD;
        if (!publicStatus && !l.getSellerId().equals(me.id()) && !me.has(Role.RESOLVER)) throw ApiException.forbidden("Image not available");
        ListingImage i = images.findById(imageId).filter(x -> x.getListingId().equals(listingId)).orElseThrow(() -> ApiException.notFound("Image not found"));
        return new ImageFile(storage.load(i.getFilePath()), i.getContentType());
    }

    @Transactional(readOnly = true)
    public List<ListingDto> mine(AuthUser me) { return mapper.toDtos(repo.findBySellerIdOrderByCreatedAtDesc(me.id())); }

    @Transactional(readOnly = true)
    public ListingDto get(AuthUser me, Long id) {
        Listing l = repo.findById(id).orElseThrow(() -> ApiException.notFound("Listing not found"));
        boolean publicStatus = l.getStatus() == ListingStatus.APPROVED || l.getStatus() == ListingStatus.RESERVED || l.getStatus() == ListingStatus.SOLD;
        if (!publicStatus && !l.getSellerId().equals(me.id()) && !me.has(Role.RESOLVER)) throw ApiException.notFound("Listing not found");
        return mapper.toDto(l);
    }

    @Transactional(readOnly = true)
    public List<ListingDto> compare(AuthUser me, List<Long> ids) {
        if (ids.size() < 2 || ids.size() > 4) throw ApiException.badRequest("Compare between 2 and 4 products");
        return ids.stream().map(i -> get(me, i)).toList();
    }

    @Transactional(readOnly = true)
    public List<ListingDto> search(SearchFilter f) {
        Specification<Listing> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("status"), ListingStatus.APPROVED));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().toLowerCase().replace("%", " ").replace("_", " ").trim() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("brand")), like),
                        cb.like(cb.lower(root.get("model")), like), cb.like(cb.lower(root.get("description")), like)));
            }
            if (f.categoryId() != null) p.add(cb.equal(root.get("categoryId"), f.categoryId()));
            if (f.brand() != null && !f.brand().isBlank()) p.add(cb.equal(cb.lower(root.get("brand")), f.brand().toLowerCase()));
            if (f.condition() != null) p.add(cb.equal(root.get("physicalCondition"), f.condition()));
            if (f.minPrice() != null) p.add(cb.greaterThanOrEqualTo(root.get("expectedPrice"), f.minPrice()));
            if (f.maxPrice() != null) p.add(cb.lessThanOrEqualTo(root.get("expectedPrice"), f.maxPrice()));
            if (f.location() != null && !f.location().isBlank()) p.add(cb.like(cb.lower(root.get("pickupCity")), "%" + f.location().toLowerCase().replace("%", " ").trim() + "%"));
            if (f.functional() != null) p.add(cb.equal(root.get("functionalStatus"), f.functional()));
            if (f.pathway() != null) p.add(cb.equal(root.get("pathway"), f.pathway()));
            return cb.and(p.toArray(new Predicate[0]));
        };
        Sort sort = switch (f.sort() == null ? "newest" : f.sort()) {
            case "price_asc" -> Sort.by("expectedPrice").ascending(); case "price_desc" -> Sort.by("expectedPrice").descending();
            case "score" -> Sort.by("circularScore").descending(); default -> Sort.by("createdAt").descending();
        };
        int size = Math.max(1, Math.min(60, f.size() <= 0 ? 24 : f.size()));
        return mapper.toDtos(repo.findAll(spec, PageRequest.of(Math.max(0, f.page()), size, sort)).getContent());
    }

    // ---- moderation (admin)
    @Transactional(readOnly = true)
    public List<ListingDto> pending() { return mapper.toDtos(repo.findByStatusOrderByCreatedAtAsc(ListingStatus.PENDING_APPROVAL)); }

    @Transactional
    public ListingDto approve(Long adminId, Long id) {
        Listing l = repo.findById(id).orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (l.getStatus() != ListingStatus.PENDING_APPROVAL) throw ApiException.conflict("Only pending listings can be approved");
        if (!lookup.sellerVerified(l.getSellerId())) throw ApiException.conflict("Verify the seller before approving their listings");
        l.setStatus(ListingStatus.APPROVED); l.setRejectionReason(null);
        lifecycle.record(id, LifecycleStage.APPROVED, l.getPathway(), adminId, "Approved by moderation");
        passports.onApproved(l, adminId);
        if (l.isAuctionEnabled()) auctions.open(l);
        audit.log(adminId, "LISTING_APPROVED", "listings", id, l.getName());
        notifications.notify(l.getSellerId(), "Listing approved", "\"" + l.getName() + "\" is now live in the marketplace.", "LISTING");
        return mapper.toDto(l);
    }
    @Transactional
    public ListingDto reject(Long adminId, Long id, String reason) {
        Listing l = repo.findById(id).orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (l.getStatus() != ListingStatus.PENDING_APPROVAL && l.getStatus() != ListingStatus.APPROVED) throw ApiException.conflict("Listing cannot be rejected in status " + l.getStatus());
        l.setStatus(ListingStatus.REJECTED); l.setRejectionReason(reason);
        audit.log(adminId, "LISTING_REJECTED", "listings", id, reason);
        notifications.notify(l.getSellerId(), "Listing rejected", "\"" + l.getName() + "\": " + reason, "LISTING");
        return mapper.toDto(l);
    }
    @Transactional(readOnly = true)
    public List<ListingDto> all(ListingStatus status) {
        return mapper.toDtos(status == null ? repo.findAll(Sort.by("createdAt").descending()).stream().limit(500).toList() : repo.findByStatusOrderByCreatedAtAsc(status));
    }
}
