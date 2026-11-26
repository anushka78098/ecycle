package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class OrderService {
    private final PurchaseOrderRepository orders; private final ListingRepository listings; private final PaymentRepository payments; private final ShipmentRepository shipments;
    private final BuyerProfileRepository buyers; private final PurchaseRules rules; private final Lookup lookup; private final NotificationService notifications;
    private final AuditService audit; private final LifecycleService lifecycle; private final PassportService passports; private final RefundService refunds;

    private PurchaseOrder createInternal(Listing l, Long buyerId, BigDecimal amount, Long offerId, Long bidId, String address, Pathway intended) {
        rules.check(l, buyerId, intended);
        PurchaseOrder o = new PurchaseOrder();
        o.setListingId(l.getId()); o.setBuyerId(buyerId); o.setSellerId(l.getSellerId()); o.setOfferId(offerId); o.setBidId(bidId);
        o.setQuantity(l.getQuantity()); o.setAmount(amount); o.setShippingAddress(address); o.setStatus(OrderStatus.PENDING_PAYMENT);
        l.setStatus(ListingStatus.RESERVED);
        orders.save(o);
        audit.log(buyerId, "ORDER_CREATED", "orders", o.getId(), "listing=" + l.getId() + " amount=" + amount);
        notifications.notify(l.getSellerId(), "New order", "\"" + l.getName() + "\" was reserved for " + amount + ". Waiting for payment.", "ORDER");
        return o;
    }

    @Transactional
    public OrderDto buyNow(AuthUser me, OrderRequest r) {
        lookup.requireRole(me, Role.BUYER);
        Listing l = listings.findById(r.listingId()).orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (l.isAuctionEnabled()) throw ApiException.conflict("This product is sold through a reverse auction; place a bid instead");
        return toDto(createInternal(l, me.id(), l.getExpectedPrice(), null, null, r.shippingAddress(), null));
    }
    @Transactional
    public OrderDto createFromOffer(Listing l, Offer o) { return toDto(createInternal(l, o.getBuyerId(), o.getAmount(), o.getId(), null, null, null)); }
    @Transactional
    public OrderDto createFromBid(Listing l, Bid b) { return toDto(createInternal(l, b.getBidderId(), b.getPrice(), null, b.getId(), null, b.getPathway())); }

    private PurchaseOrder find(Long id) { return orders.findById(id).orElseThrow(() -> ApiException.notFound("Order not found")); }
    private static void requireParty(AuthUser me, PurchaseOrder o) {
        if (!me.id().equals(o.getBuyerId()) && !me.id().equals(o.getSellerId()) && !me.has(Role.RESOLVER)) throw ApiException.forbidden("You are not part of this order");
    }

    @Transactional
    public OrderDto setAddress(AuthUser me, Long id, String address) {
        PurchaseOrder o = find(id);
        if (!o.getBuyerId().equals(me.id())) throw ApiException.forbidden("Only the buyer can set the delivery address");
        if (o.getStatus() != OrderStatus.PENDING_PAYMENT) throw ApiException.conflict("The address can only change before payment");
        o.setShippingAddress(address);
        return toDto(o);
    }

    @Transactional
    public OrderDto cancel(AuthUser me, Long id, String reason) {
        PurchaseOrder o = find(id); requireParty(me, o);
        Listing l = listings.findById(o.getListingId()).orElseThrow();
        String why = reason == null || reason.isBlank() ? "no reason given" : reason;
        switch (o.getStatus()) {
            case PENDING_PAYMENT -> o.setStatus(OrderStatus.CANCELLED);
            case PAID -> {
                Refund rf = refunds.refund(o, o.getAmount(), "Order cancelled: " + why, null, me.id());
                if (rf.getStatus() != RefundStatus.PROCESSED) throw ApiException.conflict("The refund could not be processed; the order was not cancelled");
                o.setStatus(OrderStatus.REFUNDED);
            }
            default -> throw ApiException.conflict("An order in status " + o.getStatus() + " cannot be cancelled; raise a dispute instead");
        }
        o.setCancelReason(why);
        l.setStatus(ListingStatus.APPROVED);
        if (o.getBidId() != null) l.setAuctionEnabled(false);
        audit.log(me.id(), "ORDER_CANCELLED", "orders", id, why);
        Long other = me.id().equals(o.getBuyerId()) ? o.getSellerId() : o.getBuyerId();
        notifications.notify(other, "Order cancelled", "Order #" + id + " for \"" + l.getName() + "\" was cancelled: " + why, "ORDER");
        return toDto(o);
    }

    @Transactional
    public OrderDto updateShipment(AuthUser me, Long id, ShipmentRequest r) {
        PurchaseOrder o = find(id);
        if (!o.getSellerId().equals(me.id())) throw ApiException.forbidden("Only the seller can update shipping");
        if (o.getStatus() != OrderStatus.PAID && o.getStatus() != OrderStatus.SHIPPED) throw ApiException.conflict("Shipping can only be updated for paid orders");
        if (r.status() == ShipmentStatus.DELIVERED) throw ApiException.badRequest("Delivery is confirmed by the buyer");
        Listing l = listings.findById(o.getListingId()).orElseThrow();
        boolean specialist = "SPECIALIST_PICKUP".equalsIgnoreCase(r.carrier().trim());
        if (l.isShippingBlocked()) {
            if (!specialist) throw ApiException.conflict("Normal shipping is BLOCKED for this hazardous product. Use carrier SPECIALIST_PICKUP with " + l.getSpecialistRequired());
            boolean ok = buyers.findByUserId(o.getBuyerId()).map(b -> b.isVerified() && b.getBuyerType() == BuyerType.RECYCLER).orElse(false);
            if (!ok) throw ApiException.conflict("The buyer is not a verified specialist recycler");
        } else if (specialist) throw ApiException.badRequest("SPECIALIST_PICKUP is only for hazardous products");
        boolean resale = l.getPathway() == Pathway.REUSE || l.getPathway() == Pathway.REPAIR || l.getPathway() == Pathway.REFURBISH;
        if (l.isRequiresSanitization() && !l.isSanitizationVerified() && resale) throw ApiException.conflict("Data sanitization must be verified before shipping this device");
        Shipment s = shipments.findByOrderId(id).orElseGet(Shipment::new);
        s.setOrderId(id); s.setCarrier(r.carrier().trim()); s.setTrackingNo(r.trackingNo()); s.setStatus(r.status()); s.setSpecialistHandling(specialist);
        shipments.save(s);
        if (r.status() != ShipmentStatus.PENDING) o.setStatus(OrderStatus.SHIPPED);
        audit.log(me.id(), "SHIPMENT_UPDATED", "orders", id, r.status() + " via " + r.carrier());
        notifications.notify(o.getBuyerId(), "Shipment update", "Order #" + id + " is " + r.status() + (r.trackingNo() == null ? "" : " (tracking " + r.trackingNo() + ")"), "ORDER");
        return toDto(o);
    }

    @Transactional
    public OrderDto confirmDelivery(AuthUser me, Long id) {
        PurchaseOrder o = find(id);
        if (!o.getBuyerId().equals(me.id())) throw ApiException.forbidden("Only the buyer can confirm delivery");
        if (o.getStatus() != OrderStatus.SHIPPED) throw ApiException.conflict("Only shipped orders can be confirmed as delivered");
        Listing l = listings.findById(o.getListingId()).orElseThrow();
        o.setStatus(OrderStatus.DELIVERED); l.setStatus(ListingStatus.SOLD);
        shipments.findByOrderId(id).ifPresent(s -> s.setStatus(ShipmentStatus.DELIVERED));
        lifecycle.record(l.getId(), LifecycleStage.DELIVERED, l.getPathway(), me.id(), "Delivery confirmed by buyer");
        passports.onDelivered(l, me.id());
        audit.log(me.id(), "DELIVERY_CONFIRMED", "orders", id, null);
        notifications.notify(o.getSellerId(), "Delivery confirmed", "Order #" + id + " was received. Funds are now final unless a dispute is raised.", "ORDER");
        return toDto(o);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> mine(AuthUser me, String as) {
        List<PurchaseOrder> os = "seller".equalsIgnoreCase(as) ? orders.findBySellerIdOrderByCreatedAtDesc(me.id()) : orders.findByBuyerIdOrderByCreatedAtDesc(me.id());
        return os.stream().map(this::toDto).toList();
    }
    @Transactional(readOnly = true)
    public List<OrderDto> all() { return orders.findTop500ByOrderByCreatedAtDesc().stream().map(this::toDto).toList(); }
    @Transactional(readOnly = true)
    public OrderDto get(AuthUser me, Long id) {
        PurchaseOrder o = find(id);
        if (!me.isStaff()) requireParty(me, o);
        return toDto(o);
    }

    public OrderDto toDto(PurchaseOrder o) {
        Listing l = listings.findById(o.getListingId()).orElseThrow();
        Payment p = payments.findFirstByOrderIdOrderByIdDesc(o.getId()).orElse(null);
        Shipment s = shipments.findByOrderId(o.getId()).orElse(null);
        return new OrderDto(o.getId(), l.getId(), l.getName(), o.getBuyerId(), lookup.name(o.getBuyerId()), o.getSellerId(), lookup.name(o.getSellerId()), o.getAmount(),
                o.getStatus(), o.getShippingAddress(), p == null ? null : p.getId(), p == null ? null : p.getStatus(), p == null ? null : p.getReceiptNo(),
                s == null ? null : s.getCarrier(), s == null ? null : s.getTrackingNo(), s == null ? null : s.getStatus(), s != null && s.isSpecialistHandling(),
                l.isShippingBlocked(), o.getCreatedAt());
    }
}
