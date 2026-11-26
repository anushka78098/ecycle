package com.ecycle.backend.controller;

import com.ecycle.backend.config.Sec;
import com.ecycle.backend.service.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class ListingController {
    private final ListingService listings; private final LifecycleService lifecycle; private final AdminService admin; private final HazardService hazard;
    private final PassportService passports; private final AuctionService auctions; private final com.ecycle.backend.config.AppProps props;

    @GetMapping("/categories")
    public List<CategoryDto> categories() { return admin.categories(true); }

    @PostMapping("/listings") @PreAuthorize("hasRole('SELLER')") @ResponseStatus(HttpStatus.CREATED)
    public ListingDto create(@Valid @RequestBody ListingRequest r) { return listings.create(Sec.me(), r); }
    @GetMapping("/listings/mine") @PreAuthorize("hasRole('SELLER')")
    public List<ListingDto> mine() { return listings.mine(Sec.me()); }
    @GetMapping("/listings")
    public List<ListingDto> search(@RequestParam(required = false) String q, @RequestParam(required = false) Long categoryId, @RequestParam(required = false) String brand,
                                   @RequestParam(required = false) ItemCondition condition, @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
                                   @RequestParam(required = false) String location, @RequestParam(required = false) FunctionalStatus functional, @RequestParam(required = false) Pathway pathway,
                                   @RequestParam(required = false) String sort, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size) {
        return listings.search(new ListingService.SearchFilter(q, categoryId, brand, condition, minPrice, maxPrice, location, functional, pathway, sort, page, size));
    }
    @GetMapping("/listings/compare")
    public List<ListingDto> compare(@RequestParam List<Long> ids) { return listings.compare(Sec.me(), ids); }
    @GetMapping("/listings/{id}")
    public ListingDto get(@PathVariable Long id) { return listings.get(Sec.me(), id); }
    @PutMapping("/listings/{id}") @PreAuthorize("hasRole('SELLER')")
    public ListingDto update(@PathVariable Long id, @Valid @RequestBody ListingRequest r) { return listings.update(Sec.me(), id, r); }
    @DeleteMapping("/listings/{id}") @PreAuthorize("hasRole('SELLER')")
    public Msg withdraw(@PathVariable Long id) { listings.withdraw(Sec.me(), id); return new Msg("Listing withdrawn"); }

    @PostMapping(value = "/listings/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasRole('SELLER')")
    public List<Long> upload(@PathVariable Long id, @RequestParam("files") List<MultipartFile> files) { return listings.uploadImages(Sec.me(), id, files); }
    @GetMapping("/listings/{id}/images/{imageId}")
    public ResponseEntity<Resource> image(@PathVariable Long id, @PathVariable Long imageId) {
        var f = listings.image(Sec.me(), id, imageId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType())).header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600").body(f.resource());
    }

    @GetMapping("/listings/{id}/lifecycle")
    public List<LifecycleDto> lifecycle(@PathVariable Long id) { listings.get(Sec.me(), id); return lifecycle.timeline(id, Sec.me()); }
    @GetMapping("/listings/{id}/passport")
    public PassportDto passport(@PathVariable Long id) { listings.get(Sec.me(), id); return passports.forListing(Sec.me(), id); }
    @GetMapping("/listings/{id}/auction")
    public AuctionDto auction(@PathVariable Long id) { listings.get(Sec.me(), id); return auctions.forListing(Sec.me(), id); }

    @GetMapping("/listings/{id}/hazard")
    public List<HazardDto> hazardHistory(@PathVariable Long id) { return hazard.history(Sec.me(), id); }
    @PostMapping("/listings/{id}/hazard")
    public HazardDto reassess(@PathVariable Long id, @Valid @RequestBody HazardAssessRequest r) { return hazard.reassess(Sec.me(), id, r.answers()); }
    @GetMapping("/hazard/rules")
    public List<HazardRuleDto> rules() { return hazard.listRules(); }
    @PostMapping("/listings/{id}/sanitization") @PreAuthorize("hasRole('SELLER')")
    public SanitizationDto submitSanitization(@PathVariable Long id, @Valid @RequestBody SanitizationRequest r) {
        if (props.isRequireChecklist()) throw com.ecycle.backend.exception.ApiException.conflict("Complete the data-wipe checklist (POST /api/listings/{id}/wipe-checklist) instead");
        return hazard.submit(Sec.me(), id, r);
    }
    @GetMapping("/listings/{id}/sanitization")
    public List<SanitizationDto> sanitization(@PathVariable Long id) { return hazard.forListing(Sec.me(), id); }
    @GetMapping("/listings/{id}/sanitization/certificate")
    public SanitizationDto certificate(@PathVariable Long id) { return hazard.certificate(Sec.me(), id); }
}
