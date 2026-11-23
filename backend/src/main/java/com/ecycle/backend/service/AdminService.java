package com.ecycle.backend.service;

import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AdminService {
    private final UserRepository users; private final SellerProfileRepository sellers; private final BuyerProfileRepository buyers; private final ProductCategoryRepository categories;
    private final EnvironmentalFactorRepository factors; private final AuditLogRepository audits; private final AuthService auth; private final AuditService audit;
    private final NotificationService notifications; private final Lookup lookup; private final ListingRepository listings;

    @Transactional(readOnly = true)
    public List<AdminUserDto> users(String q, Role role) {
        return users.findTop300ByOrderByIdDesc().stream()
                .filter(u -> q == null || q.isBlank() || u.getEmail().toLowerCase().contains(q.toLowerCase()) || u.getFullName().toLowerCase().contains(q.toLowerCase()))
                .filter(u -> role == null || u.hasRole(role)).map(this::dto).toList();
    }
    private AdminUserDto dto(AppUser u) {
        var sp = sellers.findByUserId(u.getId()).orElse(null); var bp = buyers.findByUserId(u.getId()).orElse(null);
        return new AdminUserDto(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.roleSet(), u.getStatus(), sp == null ? null : sp.getVerificationStatus(),
                bp == null ? null : bp.getBuyerType(), bp != null && bp.isVerified(), u.getCreatedAt());
    }

    @Transactional
    public AdminUserDto setStatus(Long adminId, Long userId, boolean active) {
        if (adminId.equals(userId)) throw ApiException.badRequest("You cannot change your own account status");
        AppUser u = lookup.user(userId);
        u.setStatus(active ? AccountStatus.ACTIVE : AccountStatus.SUSPENDED);
        audit.log(adminId, active ? "USER_REACTIVATED" : "USER_SUSPENDED", "users", userId, u.getEmail());
        if (!active) notifications.notify(userId, "Account suspended", "Your account was suspended by an administrator.", "ACCOUNT");
        return dto(u);
    }

    @Transactional
    public AdminUserDto createStaff(Long adminId, StaffRequest r) {
        if (r.role() != Role.RESOLVER) throw ApiException.badRequest("Staff accounts must be RESOLVER");
        if (users.existsByEmailIgnoreCase(r.email())) throw ApiException.conflict("Email already registered");
        AppUser u = auth.createUser(r.email(), r.password(), r.fullName(), null, r.role());
        audit.log(adminId, "STAFF_CREATED", "users", u.getId(), r.role().name());
        return dto(u);
    }

    @Transactional
    public AdminUserDto verifySeller(Long adminId, Long userId, boolean approve) {
        SellerProfile p = sellers.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Seller profile not found"));
        p.setVerificationStatus(approve ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED); p.setVerifiedAt(approve ? Instant.now() : null);
        audit.log(adminId, approve ? "SELLER_VERIFIED" : "SELLER_REJECTED", "users", userId, null);
        notifications.notify(userId, approve ? "Seller account verified" : "Seller verification rejected", approve ? "Your listings can now be approved." : "Contact support for details.", "ACCOUNT");
        return dto(lookup.user(userId));
    }
    /** Sets the buyer's verified flag and the verified recovery capability / environmental rating used by the Circular Bid Score. */
    @Transactional
    public AdminUserDto verifyBuyer(Long adminId, Long userId, VerifyBuyerRequest r) {
        BuyerProfile p = buyers.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Buyer profile not found"));
        p.setVerified(r.approve()); p.setRecoveryCapability(r.approve() ? r.recoveryCapability() : 0); p.setEnvironmentalRating(r.approve() ? r.environmentalRating() : 0);
        audit.log(adminId, r.approve() ? "BUYER_VERIFIED" : "BUYER_UNVERIFIED", "users", userId, "recovery=" + r.recoveryCapability() + " env=" + r.environmentalRating());
        notifications.notify(userId, r.approve() ? "Buyer account verified" : "Buyer verification removed", r.approve() ? "You can now bid in reverse auctions." : "You can no longer bid in auctions.", "ACCOUNT");
        return dto(lookup.user(userId));
    }

    // ---- categories
    @Transactional(readOnly = true)
    public List<CategoryDto> categories(boolean onlyActive) {
        return (onlyActive ? categories.findByActiveTrueOrderByName() : categories.findAll().stream().sorted(Comparator.comparing(ProductCategory::getName)).toList()).stream().map(AdminService::cat).toList();
    }
    @Transactional
    public CategoryDto saveCategory(Long adminId, Long id, CategoryRequest r) {
        ProductCategory c = id == null ? new ProductCategory() : categories.findById(id).orElseThrow(() -> ApiException.notFound("Category not found"));
        c.setName(r.name().trim()); c.setDescription(r.description()); c.setRepairabilityBase(r.repairabilityBase()); c.setComponentRecoveryBase(r.componentRecoveryBase());
        c.setLifeMonths(r.lifeMonths()); c.setDefaultWeightKg(r.defaultWeightKg()); c.setDismantlable(r.dismantlable()); c.setActive(r.active());
        categories.save(c);
        if (id == null && factors.findByCategoryId(c.getId()).isEmpty()) {   // new categories start with conservative generic factors that admins can tune
            EnvironmentalFactor f = new EnvironmentalFactor(); f.setCategoryId(c.getId()); f.setCarbonReusePerKg(new BigDecimal("5")); f.setCarbonRepairPerKg(new BigDecimal("3.5"));
            f.setCarbonRefurbishPerKg(new BigDecimal("4")); f.setCarbonRecyclePerKg(new BigDecimal("2")); f.setMetalsRecoveryPct(new BigDecimal("15")); f.setSourceNote("Generic default; please review"); f.setUpdatedBy(adminId); factors.save(f);
        }
        audit.log(adminId, id == null ? "CATEGORY_CREATED" : "CATEGORY_UPDATED", "product_categories", c.getId(), c.getName());
        return cat(c);
    }
    private static CategoryDto cat(ProductCategory c) {
        return new CategoryDto(c.getId(), c.getName(), c.getDescription(), c.getRepairabilityBase(), c.getComponentRecoveryBase(), c.getLifeMonths(), c.getDefaultWeightKg(), c.isDismantlable(), c.isActive());
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> audit(String q) {
        List<AuditLog> logs = q == null || q.isBlank() ? audits.findTop300ByOrderByIdDesc() : audits.findTop300ByActionContainingIgnoreCaseOrderByIdDesc(q.trim());
        Map<Long, String> names = lookup.names(logs.stream().map(AuditLog::getActorId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return logs.stream().map(a -> new AuditLogDto(a.getId(), a.getActorId(), a.getActorId() == null ? "System" : names.getOrDefault(a.getActorId(), "#" + a.getActorId()), a.getAction(), a.getEntityType(), a.getEntityId(), a.getDetail(), a.getCreatedAt())).toList();
    }
    @Transactional(readOnly = true)
    public List<AdminUserDto> resolvers() { return users.findByRolesName(Role.RESOLVER.name()).stream().map(this::dto).toList(); }
}
