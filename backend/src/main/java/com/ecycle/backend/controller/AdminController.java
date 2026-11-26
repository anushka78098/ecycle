package com.ecycle.backend.controller;

import com.ecycle.backend.config.Sec;
import com.ecycle.backend.service.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Every route under /api/admin is restricted to ROLE_RESOLVER (resolvers also moderate listings and users) in SecurityConfig. */
@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor
public class AdminController {
    private final AdminService admin; private final ListingService listings; private final OrderService orders; private final DisputeService disputes;
    private final EnvironmentalService env; private final ScoreConfigService config; private final HazardService hazard;

    @GetMapping("/users")
    public List<AdminUserDto> users(@RequestParam(required = false) String q, @RequestParam(required = false) Role role) { return admin.users(q, role); }
    @PutMapping("/users/{id}/status")
    public AdminUserDto status(@PathVariable Long id, @RequestParam boolean active) { return admin.setStatus(Sec.me().id(), id, active); }
    @PostMapping("/staff") @ResponseStatus(HttpStatus.CREATED)
    public AdminUserDto staff(@Valid @RequestBody StaffRequest r) { return admin.createStaff(Sec.me().id(), r); }
    @PostMapping("/sellers/{userId}/verify")
    public AdminUserDto verifySeller(@PathVariable Long userId, @RequestParam boolean approve) { return admin.verifySeller(Sec.me().id(), userId, approve); }
    @PostMapping("/buyers/{userId}/verify")
    public AdminUserDto verifyBuyer(@PathVariable Long userId, @Valid @RequestBody VerifyBuyerRequest r) { return admin.verifyBuyer(Sec.me().id(), userId, r); }
    @GetMapping("/resolvers")
    public List<AdminUserDto> resolvers() { return admin.resolvers(); }

    @GetMapping("/listings")
    public List<ListingDto> allListings(@RequestParam(required = false) ListingStatus status) { return listings.all(status); }
    @GetMapping("/listings/pending")
    public List<ListingDto> pending() { return listings.pending(); }
    @PostMapping("/listings/{id}/approve")
    public ListingDto approve(@PathVariable Long id) { return listings.approve(Sec.me().id(), id); }
    @PostMapping("/listings/{id}/reject")
    public ListingDto reject(@PathVariable Long id, @Valid @RequestBody RejectRequest r) { return listings.reject(Sec.me().id(), id, r.reason()); }

    @GetMapping("/categories")
    public List<CategoryDto> categories() { return admin.categories(false); }
    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto addCategory(@Valid @RequestBody CategoryRequest r) { return admin.saveCategory(Sec.me().id(), null, r); }
    @PutMapping("/categories/{id}")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest r) { return admin.saveCategory(Sec.me().id(), id, r); }

    @GetMapping("/orders")
    public List<OrderDto> orders() { return orders.all(); }
    @GetMapping("/audit")
    public List<AuditLogDto> audit(@RequestParam(required = false) String q) { return admin.audit(q); }

    @GetMapping("/config")
    public List<ConfigEntryDto> config() { return config.list(); }
    @PutMapping("/config/{key}")
    public ConfigEntryDto updateConfig(@PathVariable String key, @Valid @RequestBody ConfigUpdate r) { return config.update(Sec.me().id(), key, r.value()); }
    @GetMapping("/env-factors")
    public List<EnvFactorDto> factors() { return env.listFactors(); }
    @PutMapping("/env-factors")
    public EnvFactorDto saveFactor(@Valid @RequestBody EnvFactorRequest r) { return env.upsert(Sec.me().id(), r); }

    @GetMapping("/hazard/rules")
    public List<HazardRuleDto> rules() { return hazard.listRules(); }
    @PostMapping("/hazard/rules") @ResponseStatus(HttpStatus.CREATED)
    public HazardRuleDto addRule(@Valid @RequestBody HazardRuleRequest r) { return hazard.saveRule(Sec.me().id(), null, r); }
    @PutMapping("/hazard/rules/{id}")
    public HazardRuleDto updateRule(@PathVariable Long id, @Valid @RequestBody HazardRuleRequest r) { return hazard.saveRule(Sec.me().id(), id, r); }
    @GetMapping("/sanitization/pending")
    public List<SanitizationDto> pendingSanitization() { return hazard.pending(); }
    @PostMapping("/sanitization/{id}/verify")
    public SanitizationDto verifySanitization(@PathVariable Long id, @Valid @RequestBody VerifyRequest r) { return hazard.verify(Sec.me().id(), id, r); }
}
