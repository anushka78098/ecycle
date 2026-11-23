package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository payments; private final PurchaseOrderRepository orders; private final ListingRepository listings; private final PaymentGateway gateway;
    private final OrderService orderService; private final LifecycleService lifecycle; private final PassportService passports; private final NotificationService notifications;
    private final AuditService audit; private final Lookup lookup;

    @Transactional
    public PaymentInitDto initiate(AuthUser me, Long orderId) {
        PurchaseOrder o = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!o.getBuyerId().equals(me.id())) throw ApiException.forbidden("Only the buyer can pay for this order");
        if (o.getStatus() != OrderStatus.PENDING_PAYMENT) throw ApiException.conflict("Order is " + o.getStatus());
        if (o.getShippingAddress() == null || o.getShippingAddress().isBlank()) throw ApiException.badRequest("Set a delivery address before paying");
        Payment p = payments.findFirstByOrderIdOrderByIdDesc(orderId).filter(x -> x.getStatus() == PaymentStatus.CREATED).orElse(null);
        if (p == null) {
            p = new Payment(); p.setOrderId(orderId); p.setProvider(gateway.name()); p.setAmount(o.getAmount());
            p.setProviderOrderId(gateway.createOrder(o.getAmount(), "order-" + orderId + "-" + UUID.randomUUID().toString().substring(0, 8)));
            payments.save(p);
            audit.log(me.id(), "PAYMENT_INITIATED", "orders", orderId, gateway.name() + " amount=" + o.getAmount());
        }
        return new PaymentInitDto(p.getId(), p.getProvider(), p.getProviderOrderId(), p.getAmount(), "INR", gateway.keyId(), gateway.sandbox());
    }

    /** Server-side verification: the amount comes from our own order, the signature is checked against the provider secret, and failures are persisted. */
    @Transactional(noRollbackFor = ApiException.class)
    public OrderDto verify(AuthUser me, Long paymentId, PaymentVerifyRequest r) {
        Payment p = payments.findById(paymentId).orElseThrow(() -> ApiException.notFound("Payment not found"));
        PurchaseOrder o = orders.findById(p.getOrderId()).orElseThrow();
        if (!o.getBuyerId().equals(me.id())) throw ApiException.forbidden("Not your payment");
        if (p.getStatus() == PaymentStatus.VERIFIED) return orderService.toDto(o);   // idempotent
        if (p.getStatus() != PaymentStatus.CREATED) throw ApiException.conflict("Payment is " + p.getStatus());
        if (o.getStatus() != OrderStatus.PENDING_PAYMENT) throw ApiException.conflict("Order is " + o.getStatus());
        if (!gateway.verify(p.getProviderOrderId(), r.providerPaymentId(), r.signature())) {
            p.setStatus(PaymentStatus.FAILED);
            audit.log(me.id(), "PAYMENT_VERIFICATION_FAILED", "orders", o.getId(), "provider=" + p.getProvider());
            throw ApiException.badRequest("Payment signature verification failed");
        }
        p.setStatus(PaymentStatus.VERIFIED); p.setProviderPaymentId(r.providerPaymentId()); p.setVerifiedAt(Instant.now());
        p.setReceiptNo("RCPT-" + LocalDate.now(ZoneOffset.UTC).toString().replace("-", "") + "-" + String.format("%06d", p.getId()));
        o.setStatus(OrderStatus.PAID);
        Listing l = listings.findById(o.getListingId()).orElseThrow();
        lifecycle.record(l.getId(), LifecycleStage.PURCHASED, l.getPathway(), me.id(), "Payment verified (" + p.getReceiptNo() + ")");
        passports.onPurchased(l, me.id(), p.getAmount());
        audit.log(me.id(), "PAYMENT_VERIFIED", "orders", o.getId(), p.getReceiptNo());
        notifications.notify(o.getSellerId(), "Payment received", "Order #" + o.getId() + " (" + l.getName() + ") is paid. Please arrange shipping.", "PAYMENT");
        notifications.notify(o.getBuyerId(), "Payment confirmed", "Receipt " + p.getReceiptNo() + " for " + p.getAmount(), "PAYMENT");
        return orderService.toDto(o);
    }

    /** Sandbox only: simulates the provider's checkout callback, then runs the exact same server-side verification as a real payment. */
    @Transactional(noRollbackFor = ApiException.class)
    public OrderDto mockConfirm(AuthUser me, Long paymentId) {
        if (!(gateway instanceof MockPaymentGateway mock)) throw ApiException.notFound("Not available with the configured payment provider");
        Payment p = payments.findById(paymentId).orElseThrow(() -> ApiException.notFound("Payment not found"));
        String payId = "mock_pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        return verify(me, paymentId, new PaymentVerifyRequest(payId, mock.sign(p.getProviderOrderId(), payId)));
    }

    @Transactional(readOnly = true)
    public ReceiptDto receipt(AuthUser me, Long orderId) {
        PurchaseOrder o = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!me.has(Role.RESOLVER) && !me.id().equals(o.getBuyerId()) && !me.id().equals(o.getSellerId())) throw ApiException.forbidden("Not allowed");
        Payment p = payments.findByOrderIdOrderByIdDesc(orderId).stream().filter(x -> x.getReceiptNo() != null).findFirst().orElseThrow(() -> ApiException.notFound("No receipt: payment not verified"));
        Listing l = listings.findById(o.getListingId()).orElseThrow();
        return new ReceiptDto(p.getReceiptNo(), orderId, l.getName(), lookup.name(o.getBuyerId()), lookup.name(o.getSellerId()), p.getAmount(), p.getProvider(), p.getProviderPaymentId(), p.getVerifiedAt());
    }
}
