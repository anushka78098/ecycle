package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Enums.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Small read helpers shared by services (names, labels, profile flags). */
@Component @RequiredArgsConstructor
public class Lookup {
    private final UserRepository users; private final SellerProfileRepository sellers; private final BuyerProfileRepository buyers;
    private final ProductCategoryRepository categories;

    public AppUser user(Long id) { return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found")); }
    public String name(Long id) { return id == null ? null : users.findById(id).map(AppUser::getFullName).orElse("User #" + id); }
    public Map<Long, String> names(Collection<Long> ids) {
        Map<Long, String> m = new HashMap<>();
        users.findAllById(new HashSet<>(ids)).forEach(u -> m.put(u.getId(), u.getFullName()));
        return m;
    }
    /** Privacy-aware label: real name for the viewer themself and for admins, an anonymous handle for everyone else. */
    public String label(Long id, AuthUser viewer) {
        if (id == null) return "System";
        if (viewer != null && (viewer.id().equals(id) || viewer.has(Role.RESOLVER))) return name(id);
        return "User #" + id;
    }
    public boolean sellerVerified(Long uid) { return sellers.findByUserId(uid).map(s -> s.getVerificationStatus() == VerificationStatus.VERIFIED).orElse(false); }
    public Optional<BuyerProfile> buyer(Long uid) { return buyers.findByUserId(uid); }
    public ProductCategory category(Long id) { return categories.findById(id).orElseThrow(() -> ApiException.notFound("Category not found")); }
    public void requireRole(AuthUser u, Role r) { if (!u.has(r)) throw ApiException.forbidden("Requires role " + r); }
}
