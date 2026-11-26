package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every figure is computed from live database queries; nothing is hard-coded. */
@Service @RequiredArgsConstructor
public class DashboardService {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Set<OrderStatus> EARNED = Set.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.DISPUTED);
    private final ListingRepository listings; private final OfferRepository offers; private final PurchaseOrderRepository orders; private final WishlistRepository wishlist;
    private final DisputeRepository disputes; private final UserRepository users; private final SellerProfileRepository sellers; private final BuyerProfileRepository buyers;
    private final AuditLogRepository audits; private final ProductCategoryRepository categories; private final EnvironmentalService env; private final HazardInspectionRepository hazards;
    private final SanitizationRecordRepository sanitization; private final ListingMapper mapper;

    public DashboardDto forRole(AuthUser me, String role) {
        Role r; try { r = role == null ? null : Role.valueOf(role.toUpperCase()); } catch (IllegalArgumentException e) { throw ApiException.forbidden("Unknown role " + role); }
        if (r == null || !me.has(r)) throw ApiException.forbidden("You do not have the " + role + " role");
        return switch (r) { case SELLER -> seller(me); case BUYER -> buyer(me); case RESOLVER -> resolver(me); };
    }
    private static Map<String, Number> monthly(Collection<PurchaseOrder> os) {
        Map<String, Number> m = new TreeMap<>();
        for (PurchaseOrder o : os) m.merge(MONTH.format(o.getCreatedAt().atZone(ZoneOffset.UTC)), o.getAmount().doubleValue(), (a, b) -> a.doubleValue() + b.doubleValue());
        return m;
    }
    private static <T> Map<String, Number> count(Collection<T> xs, java.util.function.Function<T, String> key) {
        Map<String, Number> m = new LinkedHashMap<>(); for (T x : xs) m.merge(key.apply(x), 1L, (a, b) -> a.longValue() + 1); return m;
    }
    private static Map<String, Object> orderRow(PurchaseOrder o, String name) {
        Map<String, Object> m = new LinkedHashMap<>(); m.put("order", o.getId()); m.put("product", name); m.put("amount", o.getAmount()); m.put("status", o.getStatus()); m.put("date", o.getCreatedAt()); return m;
    }

    private DashboardDto seller(AuthUser me) {
        List<Listing> ls = listings.findBySellerIdOrderByCreatedAtDesc(me.id());
        List<PurchaseOrder> os = orders.findBySellerIdOrderByCreatedAtDesc(me.id());
        Set<Long> ids = ls.stream().map(Listing::getId).collect(Collectors.toSet());
        var imp = env.summary("seller", me.id());
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("totalListings", (long) ls.size()); s.put("activeListings", ls.stream().filter(l -> l.getStatus() == ListingStatus.APPROVED).count());
        s.put("pendingApprovals", ls.stream().filter(l -> l.getStatus() == ListingStatus.PENDING_APPROVAL).count());
        s.put("productsSold", orders.countBySellerIdAndStatusIn(me.id(), List.of(OrderStatus.DELIVERED)));
        s.put("pendingOffers", ids.isEmpty() ? 0L : offers.countByListingIdInAndStatus(ids, OfferStatus.PENDING));
        s.put("totalEarnings", orders.sumSellerAmount(me.id(), EARNED)); s.put("wasteDivertedKg", imp.ewasteDivertedKg()); s.put("carbonAvoidedKg", imp.carbonAvoidedKg());
        Map<String, Map<String, Number>> charts = new LinkedHashMap<>();
        charts.put("Listings by status", count(ls, l -> l.getStatus().name()));
        Map<Long, String> cn = categories.findAll().stream().collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName));
        charts.put("Listings by category", count(ls, l -> cn.get(l.getCategoryId())));
        charts.put("Earnings by month", monthly(os.stream().filter(o -> EARNED.contains(o.getStatus())).toList()));
        Map<Long, String> names = listings.findByIdIn(os.stream().map(PurchaseOrder::getListingId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(Listing::getId, Listing::getName));
        return new DashboardDto("SELLER", s, charts, os.stream().limit(8).map(o -> orderRow(o, names.get(o.getListingId()))).toList());
    }

    private DashboardDto buyer(AuthUser me) {
        List<PurchaseOrder> os = orders.findByBuyerIdOrderByCreatedAtDesc(me.id());
        var imp = env.summary("buyer", me.id());
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("totalPurchases", os.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count());
        s.put("activeOrders", orders.countByBuyerIdAndStatusIn(me.id(), List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DISPUTED)));
        s.put("pendingOffers", offers.countByBuyerIdAndStatus(me.id(), OfferStatus.PENDING)); s.put("wishlist", wishlist.countByUserId(me.id()));
        s.put("totalSpent", orders.sumBuyerAmount(me.id(), EARNED)); s.put("wasteDivertedKg", imp.ewasteDivertedKg()); s.put("carbonAvoidedKg", imp.carbonAvoidedKg());
        Map<String, Map<String, Number>> charts = new LinkedHashMap<>();
        charts.put("Orders by status", count(os, o -> o.getStatus().name()));
        charts.put("Spend by month", monthly(os.stream().filter(o -> EARNED.contains(o.getStatus())).toList()));
        Map<Long, String> names = listings.findByIdIn(os.stream().map(PurchaseOrder::getListingId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(Listing::getId, Listing::getName));
        return new DashboardDto("BUYER", s, charts, os.stream().limit(8).map(o -> orderRow(o, names.get(o.getListingId()))).toList());
    }

    private DashboardDto resolver(AuthUser me) {
        List<DisputeStatus> active = List.of(DisputeStatus.OPEN, DisputeStatus.ASSIGNED, DisputeStatus.AWAITING_EVIDENCE, DisputeStatus.UNDER_REVIEW);
        List<DisputeStatus> fin = List.of(DisputeStatus.RESOLVED, DisputeStatus.REJECTED);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("totalDisputes", disputes.count()); s.put("openDisputes", disputes.countByStatus(DisputeStatus.OPEN));
        s.put("assignedToMe", disputes.countByResolverIdAndStatusIn(me.id(), active)); s.put("pendingEvidence", disputes.countByStatus(DisputeStatus.AWAITING_EVIDENCE));
        s.put("resolvedDisputes", disputes.countByStatus(DisputeStatus.RESOLVED));
        s.put("highPriority", disputes.countByPriorityInAndStatusNotIn(List.of(DisputePriority.HIGH, DisputePriority.CRITICAL), fin));
        Double avg = disputes.avgResolutionHours(); s.put("avgResolutionHours", avg == null ? 0.0 : Math.round(avg * 10.0) / 10.0);
        List<Dispute> all = disputes.findTop500ByOrderByCreatedAtDesc();
        Map<String, Map<String, Number>> charts = new LinkedHashMap<>();
        charts.put("Disputes by status", count(all, d -> d.getStatus().name())); charts.put("Disputes by category", count(all, d -> d.getCategory().name()));
        charts.put("Disputes by priority", count(all, d -> d.getPriority().name()));
        List<Map<String, Object>> recent = all.stream().limit(8).map(d -> { Map<String, Object> m = new LinkedHashMap<>(); m.put("dispute", d.getId()); m.put("category", d.getCategory()); m.put("priority", d.getPriority()); m.put("status", d.getStatus()); m.put("date", d.getCreatedAt()); return m; }).toList();
        return new DashboardDto("RESOLVER", s, charts, recent);
    }

    private DashboardDto admin() {
        List<Listing> ls = listings.findAll(); List<PurchaseOrder> os = orders.findTop500ByOrderByCreatedAtDesc();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("totalUsers", users.count()); s.put("sellersAwaitingVerification", sellers.findByVerificationStatus(VerificationStatus.PENDING).size());
        s.put("buyersAwaitingVerification", buyers.findByVerifiedFalse().size());
        s.put("listingsPending", listings.countByStatus(ListingStatus.PENDING_APPROVAL)); s.put("listingsLive", listings.countByStatus(ListingStatus.APPROVED));
        s.put("hazardousListings", ls.stream().filter(Listing::isShippingBlocked).count()); s.put("sanitizationPending", sanitization.findByStatusOrderByIdAsc(SanitizationStatus.SUBMITTED).size());
        s.put("orders", orders.count()); s.put("grossMerchandiseValue", orders.sumAll(EARNED)); s.put("openDisputes", disputes.countByStatusIn(List.of(DisputeStatus.OPEN, DisputeStatus.ASSIGNED, DisputeStatus.AWAITING_EVIDENCE, DisputeStatus.UNDER_REVIEW)));
        var imp = env.summary("global", null); s.put("wasteDivertedKg", imp.ewasteDivertedKg()); s.put("carbonAvoidedKg", imp.carbonAvoidedKg());
        Map<String, Map<String, Number>> charts = new LinkedHashMap<>();
        Map<String, Number> roles = new LinkedHashMap<>(); for (Role r : Role.values()) roles.put(r.name(), (long) users.findByRolesName(r.name()).size());
        charts.put("Users by role", roles); charts.put("Listings by status", count(ls, l -> l.getStatus().name()));
        charts.put("Listings by hazard level", count(ls, l -> l.getHazardLevel().name())); charts.put("Orders by status", count(os, o -> o.getStatus().name()));
        charts.put("GMV by month", monthly(os.stream().filter(o -> EARNED.contains(o.getStatus())).toList()));
        List<Map<String, Object>> recent = audits.findTop300ByOrderByIdDesc().stream().limit(10).map(a -> { Map<String, Object> m = new LinkedHashMap<>(); m.put("action", a.getAction()); m.put("entity", a.getEntityType() + "#" + a.getEntityId()); m.put("actor", a.getActorId()); m.put("date", a.getCreatedAt()); return m; }).toList();
        return new DashboardDto("ADMIN", s, charts, recent);
    }
}
