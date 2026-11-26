package com.ecycle.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class) @MockitoSettings(strictness = Strictness.LENIENT)
class DisputeServiceTest {
    @Mock DisputeRepository disputes; @Mock DisputeAssignmentRepository assignments; @Mock DisputeEvidenceRepository evidence; @Mock DisputeMessageRepository messages; @Mock DisputeResolutionRepository resolutions;
    @Mock PurchaseOrderRepository orders; @Mock ListingRepository listings; @Mock PaymentRepository payments; @Mock ShipmentRepository shipments; @Mock UserRepository users; @Mock SellerProfileRepository sellers;
    @Mock BuyerProfileRepository buyers; @Mock Lookup lookup; @Mock FileStorageService storage; @Mock RefundService refunds; @Mock NotificationService notifications; @Mock AuditService audit;
    @InjectMocks DisputeService svc;
    static final AuthUser BUYER = new AuthUser(2L, "b", Set.of(Role.BUYER)), SELLER = new AuthUser(1L, "s", Set.of(Role.SELLER)), STRANGER = new AuthUser(9L, "x", Set.of(Role.BUYER)),
            RES1 = new AuthUser(20L, "r1", Set.of(Role.RESOLVER)), RES2 = new AuthUser(21L, "r2", Set.of(Role.RESOLVER));
    PurchaseOrder order; Dispute dispute; Payment payment;

    @BeforeEach void setUp() {
        order = new PurchaseOrder(); order.setId(100L); order.setListingId(10L); order.setBuyerId(2L); order.setSellerId(1L); order.setAmount(new BigDecimal("8000")); order.setStatus(OrderStatus.DELIVERED);
        dispute = new Dispute(); dispute.setId(50L); dispute.setOrderId(100L); dispute.setRaisedBy(2L); dispute.setAgainstUser(1L); dispute.setStatus(DisputeStatus.OPEN); dispute.setPriority(DisputePriority.MEDIUM); dispute.setCategory(DisputeCategory.DAMAGED_PRODUCT);
        payment = new Payment(); payment.setId(1L); payment.setOrderId(100L); payment.setAmount(new BigDecimal("8000")); payment.setStatus(PaymentStatus.VERIFIED);
        Listing l = new Listing(); l.setId(10L); l.setName("Laptop");
        AppUser u = new AppUser(); u.setId(2L); u.setFullName("N");
        when(orders.findById(100L)).thenReturn(Optional.of(order)); when(disputes.findById(50L)).thenReturn(Optional.of(dispute)); when(disputes.save(any())).thenAnswer(i -> { Dispute d = i.getArgument(0); if (d.getId() == null) d.setId(51L); return d; });
        when(listings.findById(10L)).thenReturn(Optional.of(l)); when(lookup.user(anyLong())).thenReturn(u); when(payments.findFirstByOrderIdOrderByIdDesc(100L)).thenReturn(Optional.of(payment));
        AppUser resolver = new AppUser(); resolver.setId(20L); resolver.getRoles().add(role("RESOLVER")); when(users.findById(20L)).thenReturn(Optional.of(resolver));
        when(storage.store(any(), any(), any(), anyLong())).thenReturn(new FileStorageService.Stored("disputes/50/x.png", "x.png", "image/png", "a".repeat(64)));
        when(evidence.save(any())).thenAnswer(i -> { DisputeEvidence e = i.getArgument(0); e.setId(9L); return e; });
        when(refunds.refund(any(), any(), any(), any(), any())).thenAnswer(i -> { Refund r = new Refund(); r.setAmount(i.getArgument(1)); r.setStatus(RefundStatus.PROCESSED); return r; });
    }
    private static AppRole role(String n) { AppRole r = new AppRole(); r.setName(n); return r; }

    @Test void buyerCanRaiseDisputeAndOrderBecomesDisputed() {
        DisputeDto d = svc.create(BUYER, new DisputeRequest(100L, DisputeCategory.DAMAGED_PRODUCT, "Cracked screen"));
        assertEquals(OrderStatus.DISPUTED, order.getStatus()); assertEquals(DisputeStatus.OPEN, d.status()); assertEquals(1L, d.againstUser());
        verify(notifications).notify(eq(1L), anyString(), anyString(), eq("DISPUTE")); verify(notifications).notifyRole(eq(Role.RESOLVER), anyString(), anyString(), eq("DISPUTE"));
    }
    @Test void paymentDisputesAndHighValueOrdersAreHighPriority() {
        assertEquals(DisputePriority.HIGH, svc.create(BUYER, new DisputeRequest(100L, DisputeCategory.PAYMENT_ISSUE, "Charged twice")).priority());
    }
    @Test void strangerCannotRaiseDisputeOnSomeoneElsesOrder() { assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> svc.create(STRANGER, new DisputeRequest(100L, DisputeCategory.DAMAGED_PRODUCT, "x"))).status()); }
    @Test void unpaidOrdersCannotBeDisputed() { order.setStatus(OrderStatus.PENDING_PAYMENT); assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> svc.create(BUYER, new DisputeRequest(100L, DisputeCategory.DAMAGED_PRODUCT, "x"))).status()); }

    @Test void partiesCanSubmitEvidenceButResolversCannot() {
        MockMultipartFile f = new MockMultipartFile("file", "x.png", "image/png", new byte[20]);
        EvidenceDto e = svc.uploadEvidence(BUYER, 50L, f, EvidenceCategory.PHOTO, "damage"); assertEquals("a".repeat(64), e.sha256());
        verify(evidence).save(any(DisputeEvidence.class));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> svc.uploadEvidence(RES1, 50L, f, EvidenceCategory.PHOTO, "x")).status());
        assertThrows(ApiException.class, () -> svc.uploadEvidence(STRANGER, 50L, f, EvidenceCategory.PHOTO, "x"));
    }
    @Test void evidenceIsRejectedOnClosedDisputes() { dispute.setStatus(DisputeStatus.RESOLVED); assertThrows(ApiException.class, () -> svc.uploadEvidence(BUYER, 50L, new MockMultipartFile("f", "x.png", "image/png", new byte[20]), EvidenceCategory.PHOTO, "x")); }

    @Test void resolverCanClaimAndStatusBecomesAssigned() {
        svc.assign(RES1, 50L, null); assertEquals(20L, dispute.getResolverId()); assertEquals(DisputeStatus.ASSIGNED, dispute.getStatus()); verify(assignments).save(any(DisputeAssignment.class));
    }
    @Test void anotherResolverCannotStealAnAssignedDispute() { dispute.setResolverId(20L); assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> svc.assign(RES2, 50L, null)).status()); }
    @Test void buyersAndSellersCannotAssign() { assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> svc.assign(BUYER, 50L, null)).status()); }
    @Test void onlyAssignedResolverCanRequestEvidenceAndEvidenceRequestChangesStatus() {
        dispute.setResolverId(20L); dispute.setStatus(DisputeStatus.ASSIGNED);
        assertThrows(ApiException.class, () -> svc.post(RES2, 50L, new MessageRequest(MessageVisibility.EVIDENCE_REQUEST, "send photos")));
        svc.post(RES1, 50L, new MessageRequest(MessageVisibility.EVIDENCE_REQUEST, "send photos")); assertEquals(DisputeStatus.AWAITING_EVIDENCE, dispute.getStatus());
    }
    @Test void partiesCannotWriteInternalNotes() {
        ArgumentCaptor<DisputeMessage> c = ArgumentCaptor.forClass(DisputeMessage.class);
        svc.post(BUYER, 50L, new MessageRequest(MessageVisibility.INTERNAL, "secret")); verify(messages).save(c.capture()); assertEquals(MessageVisibility.PARTIES, c.getValue().getVisibility());
    }

    @Test void unassignedResolverCannotResolve() { dispute.setResolverId(20L); assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> svc.resolve(RES2, 50L, new ResolutionRequest(ResolutionType.FULL_REFUND, null, "x"))).status()); }
    @Test void fullRefundResolutionRefundsThePaidAmountAsASystemAction() {
        dispute.setResolverId(20L); dispute.setStatus(DisputeStatus.UNDER_REVIEW); order.setStatus(OrderStatus.DISPUTED);
        svc.resolve(RES1, 50L, new ResolutionRequest(ResolutionType.FULL_REFUND, null, "Seller shipped a damaged item"));
        verify(refunds).refund(eq(order), eq(new BigDecimal("8000")), anyString(), eq(50L), eq(20L));
        assertEquals(DisputeStatus.RESOLVED, dispute.getStatus()); assertNotNull(dispute.getResolvedAt()); assertEquals(OrderStatus.REFUNDED, order.getStatus()); verify(resolutions).save(any(DisputeResolution.class));
    }
    @Test void partialRefundNeedsAValidAmount() {
        dispute.setResolverId(20L);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> svc.resolve(RES1, 50L, new ResolutionRequest(ResolutionType.PARTIAL_REFUND, null, "x"))).status());
        assertThrows(ApiException.class, () -> svc.resolve(RES1, 50L, new ResolutionRequest(ResolutionType.PARTIAL_REFUND, new BigDecimal("8000"), "x")));
        verify(refunds, never()).refund(any(), any(), any(), any(), any());
    }
    @Test void partialRefundRefundsOnlyThatAmountAndKeepsOrderDelivered() {
        dispute.setResolverId(20L); order.setStatus(OrderStatus.DISPUTED); when(shipments.findByOrderId(100L)).thenReturn(Optional.of(shipment(ShipmentStatus.DELIVERED)));
        svc.resolve(RES1, 50L, new ResolutionRequest(ResolutionType.PARTIAL_REFUND, new BigDecimal("2500"), "Cosmetic damage")); verify(refunds).refund(eq(order), eq(new BigDecimal("2500")), anyString(), eq(50L), eq(20L)); assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }
    @Test void sellerFavourOrReplacementCannotCarryARefund() {
        dispute.setResolverId(20L);
        assertThrows(ApiException.class, () -> svc.resolve(RES1, 50L, new ResolutionRequest(ResolutionType.RESOLVED_SELLER_FAVOR, new BigDecimal("100"), "x")));
        assertThrows(ApiException.class, () -> svc.resolve(RES1, 50L, new ResolutionRequest(ResolutionType.REPLACEMENT, new BigDecimal("100"), "x")));
    }
    @Test void failedProviderRefundKeepsTheDisputeOpen() {
        dispute.setResolverId(20L); when(refunds.refund(any(), any(), any(), any(), any())).thenAnswer(i -> { Refund r = new Refund(); r.setStatus(RefundStatus.FAILED); return r; });
        assertThrows(ApiException.class, () -> svc.resolve(RES1, 50L, new ResolutionRequest(ResolutionType.FULL_REFUND, null, "x"))); assertNotEquals(DisputeStatus.RESOLVED, dispute.getStatus());
    }
    @Test void resolvedDisputesAreFinal() { dispute.setStatus(DisputeStatus.RESOLVED); dispute.setResolverId(20L); assertThrows(ApiException.class, () -> svc.resolve(RES1, 50L, new ResolutionRequest(ResolutionType.REPLACEMENT, null, "x"))); assertThrows(ApiException.class, () -> svc.reject(RES1, 50L, "no")); }
    @Test void strangersCannotViewADispute() { assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> svc.detail(STRANGER, 50L)).status()); }
    @Test void partiesDoNotSeeInternalInvestigationNotes() {
        DisputeMessage pub = new DisputeMessage(); pub.setId(1L); pub.setSenderId(20L); pub.setVisibility(MessageVisibility.PARTIES); pub.setBody("hello");
        DisputeMessage internal = new DisputeMessage(); internal.setId(2L); internal.setSenderId(20L); internal.setVisibility(MessageVisibility.INTERNAL); internal.setBody("secret");
        when(messages.findByDisputeIdOrderByCreatedAtAsc(50L)).thenReturn(List.of(pub, internal));
        assertEquals(1, svc.detail(BUYER, 50L).messages().size()); dispute.setResolverId(20L); assertEquals(2, svc.detail(RES1, 50L).messages().size());
    }
    @Test void resolversSeeNoContactDetailsOfParties() { assertNull(svc.detail(RES1, 50L).buyer().contact()); }
    private static Shipment shipment(ShipmentStatus s) { Shipment x = new Shipment(); x.setStatus(s); return x; }
}
