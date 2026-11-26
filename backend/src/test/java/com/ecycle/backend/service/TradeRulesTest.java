package com.ecycle.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

/** Purchase rules, orders/shipping (hazard routing, sanitization gating), payment verification and offers. */
@ExtendWith(MockitoExtension.class) @MockitoSettings(strictness = Strictness.LENIENT)
class TradeRulesTest {
    @Mock BuyerProfileRepository buyers; @Mock PurchaseOrderRepository orders; @Mock ListingRepository listings; @Mock PaymentRepository payments; @Mock ShipmentRepository shipments;
    @Mock PurchaseRules rulesMock; @Mock Lookup lookup; @Mock NotificationService notifications; @Mock AuditService audit; @Mock LifecycleService lifecycle; @Mock PassportService passports; @Mock RefundService refunds;
    @Mock OrderService orderService; @Mock OfferRepository offers;
    @Spy MockPaymentGateway gateway = new MockPaymentGateway();

    static final AuthUser SELLER = new AuthUser(1L, "s@x", Set.of(Role.SELLER)), BUYER = new AuthUser(2L, "b@x", Set.of(Role.BUYER)), OTHER = new AuthUser(9L, "o@x", Set.of(Role.BUYER));
    Listing listing; PurchaseOrder order;

    @BeforeEach void setUp() {
        listing = new Listing(); listing.setId(10L); listing.setSellerId(1L); listing.setName("Laptop"); listing.setStatus(ListingStatus.APPROVED); listing.setPathway(Pathway.REFURBISH);
        listing.setExpectedPrice(new BigDecimal("5000")); listing.setQuantity(1);
        order = new PurchaseOrder(); order.setId(100L); order.setListingId(10L); order.setSellerId(1L); order.setBuyerId(2L); order.setAmount(new BigDecimal("5000")); order.setStatus(OrderStatus.PAID);
        when(listings.findById(10L)).thenReturn(Optional.of(listing)); when(orders.findById(100L)).thenReturn(Optional.of(order));
        when(shipments.findByOrderId(100L)).thenReturn(Optional.empty()); when(shipments.save(any())).thenAnswer(i -> i.getArgument(0));
    }
    private BuyerProfile profile(BuyerType t, boolean verified) { BuyerProfile p = new BuyerProfile(); p.setBuyerType(t); p.setVerified(verified); return p; }

    // ---- PurchaseRules
    @Test void sellerCannotBuyOwnListing() { assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> new PurchaseRules(buyers).check(listing, 1L, null)).status()); }
    @Test void unavailableListingCannotBeBought() { listing.setStatus(ListingStatus.RESERVED); assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> new PurchaseRules(buyers).check(listing, 2L, null)).status()); }
    @Test void hazardousListingNeedsVerifiedRecycler() {
        listing.setShippingBlocked(true); listing.setSpecialistRequired("Battery recycler");
        when(buyers.findByUserId(2L)).thenReturn(Optional.of(profile(BuyerType.REFURBISHER, true)));
        assertThrows(ApiException.class, () -> new PurchaseRules(buyers).check(listing, 2L, null));
        when(buyers.findByUserId(2L)).thenReturn(Optional.of(profile(BuyerType.RECYCLER, false)));
        assertThrows(ApiException.class, () -> new PurchaseRules(buyers).check(listing, 2L, null));
        when(buyers.findByUserId(2L)).thenReturn(Optional.of(profile(BuyerType.RECYCLER, true)));
        assertDoesNotThrow(() -> new PurchaseRules(buyers).check(listing, 2L, null));
    }
    @Test void unsanitizedStorageBlocksResaleButNotRecycling() {
        listing.setRequiresSanitization(true); listing.setSanitizationVerified(false);
        assertThrows(ApiException.class, () -> new PurchaseRules(buyers).check(listing, 2L, null));
        assertDoesNotThrow(() -> new PurchaseRules(buyers).check(listing, 2L, Pathway.RECYCLE));
        listing.setSanitizationVerified(true); assertDoesNotThrow(() -> new PurchaseRules(buyers).check(listing, 2L, null));
    }

    // ---- OrderService shipping rules
    private OrderService orderSvc() { return new OrderService(orders, listings, payments, shipments, buyers, new PurchaseRules(buyers), lookup, notifications, audit, lifecycle, passports, refunds); }
    @Test void onlySellerUpdatesShipping() { assertThrows(ApiException.class, () -> orderSvc().updateShipment(BUYER, 100L, new ShipmentRequest("DHL", "T1", ShipmentStatus.SHIPPED))); }
    @Test void hazardousItemCannotShipWithNormalCarrier() {
        listing.setShippingBlocked(true); listing.setSpecialistRequired("Battery recycler");
        ApiException e = assertThrows(ApiException.class, () -> orderSvc().updateShipment(SELLER, 100L, new ShipmentRequest("DHL", "T1", ShipmentStatus.SHIPPED)));
        assertEquals(HttpStatus.CONFLICT, e.status()); assertTrue(e.getMessage().contains("BLOCKED"));
    }
    @Test void hazardousItemShipsViaSpecialistPickupToVerifiedRecycler() {
        listing.setShippingBlocked(true); when(buyers.findByUserId(2L)).thenReturn(Optional.of(profile(BuyerType.RECYCLER, true)));
        when(payments.findFirstByOrderIdOrderByIdDesc(any())).thenReturn(Optional.empty());
        orderSvc().updateShipment(SELLER, 100L, new ShipmentRequest("SPECIALIST_PICKUP", "X", ShipmentStatus.SHIPPED)); assertEquals(OrderStatus.SHIPPED, order.getStatus());
        ArgumentCaptor<Shipment> c = ArgumentCaptor.forClass(Shipment.class); verify(shipments).save(c.capture()); assertTrue(c.getValue().isSpecialistHandling());
    }
    @Test void specialistPickupIsRejectedForOrdinaryItems() { assertThrows(ApiException.class, () -> orderSvc().updateShipment(SELLER, 100L, new ShipmentRequest("SPECIALIST_PICKUP", "X", ShipmentStatus.SHIPPED))); }
    @Test void sellerCannotMarkDeliveredAndUnsanitizedResaleCannotShip() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> orderSvc().updateShipment(SELLER, 100L, new ShipmentRequest("DHL", "T", ShipmentStatus.DELIVERED))).status());
        listing.setRequiresSanitization(true); listing.setSanitizationVerified(false);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> orderSvc().updateShipment(SELLER, 100L, new ShipmentRequest("DHL", "T", ShipmentStatus.SHIPPED))).status());
    }
    @Test void deliveryConfirmationIsBuyerOnlyAndTransfersOwnership() {
        order.setStatus(OrderStatus.SHIPPED);
        assertThrows(ApiException.class, () -> orderSvc().confirmDelivery(SELLER, 100L));
        orderSvc().confirmDelivery(BUYER, 100L);
        assertEquals(OrderStatus.DELIVERED, order.getStatus()); assertEquals(ListingStatus.SOLD, listing.getStatus()); verify(passports).onDelivered(listing, 2L);
    }
    @Test void paidOrderCancellationRefundsAndReleasesListing() {
        Refund ok = new Refund(); ok.setStatus(RefundStatus.PROCESSED); when(refunds.refund(any(), any(), any(), any(), any())).thenReturn(ok);
        orderSvc().cancel(BUYER, 100L, "changed my mind");
        assertEquals(OrderStatus.REFUNDED, order.getStatus()); assertEquals(ListingStatus.APPROVED, listing.getStatus());
    }
    @Test void failedRefundBlocksCancellation() {
        Refund bad = new Refund(); bad.setStatus(RefundStatus.FAILED); when(refunds.refund(any(), any(), any(), any(), any())).thenReturn(bad);
        assertThrows(ApiException.class, () -> orderSvc().cancel(BUYER, 100L, "x"));
    }
    @Test void shippedOrdersCannotBeCancelledAndStrangersCannotCancel() {
        order.setStatus(OrderStatus.SHIPPED); assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> orderSvc().cancel(BUYER, 100L, "x")).status());
        order.setStatus(OrderStatus.PAID); assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> orderSvc().cancel(OTHER, 100L, "x")).status());
    }

    // ---- PaymentService: server-side verification
    private PaymentService paymentSvc() { return new PaymentService(payments, orders, listings, gateway, orderService, lifecycle, passports, notifications, audit, lookup); }
    private Payment payment(PaymentStatus s) { Payment p = new Payment(); p.setId(55L); p.setOrderId(100L); p.setProvider("MOCK_SANDBOX"); p.setProviderOrderId("mock_order_1"); p.setAmount(order.getAmount()); p.setStatus(s); when(payments.findById(55L)).thenReturn(Optional.of(p)); return p; }
    @Test void validSignatureVerifiesPaymentAndMarksOrderPaid() {
        order.setStatus(OrderStatus.PENDING_PAYMENT); Payment p = payment(PaymentStatus.CREATED);
        paymentSvc().verify(BUYER, 55L, new PaymentVerifyRequest("pay_1", gateway.sign("mock_order_1", "pay_1")));
        assertEquals(PaymentStatus.VERIFIED, p.getStatus()); assertEquals(OrderStatus.PAID, order.getStatus()); assertTrue(p.getReceiptNo().startsWith("RCPT-")); assertNotNull(p.getVerifiedAt());
        verify(passports).onPurchased(listing, 2L, p.getAmount());
    }
    @Test void forgedSignatureIsRejectedAndRecordedAsFailed() {
        order.setStatus(OrderStatus.PENDING_PAYMENT); Payment p = payment(PaymentStatus.CREATED);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> paymentSvc().verify(BUYER, 55L, new PaymentVerifyRequest("pay_1", "forged"))).status());
        assertEquals(PaymentStatus.FAILED, p.getStatus()); assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
    }
    @Test void signatureForADifferentPaymentIdIsRejected() {
        order.setStatus(OrderStatus.PENDING_PAYMENT); payment(PaymentStatus.CREATED);
        assertThrows(ApiException.class, () -> paymentSvc().verify(BUYER, 55L, new PaymentVerifyRequest("pay_2", gateway.sign("mock_order_1", "pay_1"))));
    }
    @Test void onlyTheBuyerCanVerifyAndReplayIsIdempotent() {
        order.setStatus(OrderStatus.PENDING_PAYMENT); payment(PaymentStatus.CREATED);
        assertThrows(ApiException.class, () -> paymentSvc().verify(OTHER, 55L, new PaymentVerifyRequest("p", "s")));
        Payment done = payment(PaymentStatus.VERIFIED); order.setStatus(OrderStatus.PAID);
        paymentSvc().verify(BUYER, 55L, new PaymentVerifyRequest("p", "anything")); assertEquals(PaymentStatus.VERIFIED, done.getStatus()); verify(passports, never()).onPurchased(any(), any(), any());
    }
}
