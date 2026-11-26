package com.ecycle.backend.service;

import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The only code path that moves money back. Dispute resolvers never call this directly: DisputeService issues a system refund after a recorded decision. */
@Service @RequiredArgsConstructor
public class RefundService {
    private static final Logger log = LoggerFactory.getLogger(RefundService.class);
    private final PaymentRepository payments; private final RefundRepository refunds; private final PaymentGateway gateway;
    private final AuditService audit; private final NotificationService notifications;

    @Transactional
    public Refund refund(PurchaseOrder o, BigDecimal amount, String reason, Long disputeId, Long actorId) {
        Payment p = payments.findFirstByOrderIdOrderByIdDesc(o.getId()).filter(x -> x.getStatus() == PaymentStatus.VERIFIED || x.getStatus() == PaymentStatus.PARTIALLY_REFUNDED)
                .orElseThrow(() -> ApiException.conflict("There is no verified payment to refund for this order"));
        BigDecimal already = refunds.sumByStatus(p.getId(), RefundStatus.PROCESSED);
        BigDecimal remaining = p.getAmount().subtract(already);
        if (amount.signum() <= 0 || amount.compareTo(remaining) > 0) throw ApiException.badRequest("Refund must be between 0.01 and the remaining refundable amount " + remaining);
        Refund r = new Refund();
        r.setPaymentId(p.getId()); r.setOrderId(o.getId()); r.setDisputeId(disputeId); r.setAmount(amount); r.setReason(reason);
        try {
            r.setProviderRefundId(gateway.refund(p.getProviderPaymentId(), amount)); r.setStatus(RefundStatus.PROCESSED);
        } catch (ApiException e) {
            log.error("Refund failed for order {}: {}", o.getId(), e.getMessage());
            r.setStatus(RefundStatus.FAILED);
        }
        refunds.save(r);
        if (r.getStatus() == RefundStatus.PROCESSED) {
            p.setStatus(already.add(amount).compareTo(p.getAmount()) >= 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
            audit.log(actorId, "REFUND_PROCESSED", "orders", o.getId(), "amount=" + amount + " dispute=" + disputeId);
            notifications.notify(o.getBuyerId(), "Refund issued", "A refund of " + amount + " was issued for order #" + o.getId() + ".", "REFUND");
        }
        return r;
    }
    @Transactional(readOnly = true)
    public List<Refund> forPayment(Long paymentId) { return refunds.findByPaymentId(paymentId); }
}
