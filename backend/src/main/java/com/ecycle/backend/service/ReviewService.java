package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.OrderStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviews; private final PurchaseOrderRepository orders; private final SellerProfileRepository sellers; private final Lookup lookup; private final AuditService audit;

    @Transactional
    public ReviewDto create(AuthUser me, ReviewRequest r) {
        PurchaseOrder o = orders.findById(r.orderId()).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (o.getStatus() != OrderStatus.DELIVERED) throw ApiException.conflict("Only delivered orders can be reviewed");
        Long reviewee;
        if (me.id().equals(o.getBuyerId())) reviewee = o.getSellerId(); else if (me.id().equals(o.getSellerId())) reviewee = o.getBuyerId(); else throw ApiException.forbidden("You are not part of this order");
        if (reviews.existsByOrderIdAndReviewerId(o.getId(), me.id())) throw ApiException.conflict("You already reviewed this order");
        Review v = new Review(); v.setOrderId(o.getId()); v.setReviewerId(me.id()); v.setRevieweeId(reviewee); v.setRating(r.rating()); v.setComment(r.comment());
        reviews.save(v);
        sellers.findByUserId(reviewee).ifPresent(sp -> sp.setTrustScore(BigDecimal.valueOf(reviews.avgRating(reviewee) / 5.0 * 100.0).setScale(1, RoundingMode.HALF_UP)));
        audit.log(me.id(), "REVIEW_CREATED", "orders", o.getId(), "rating=" + r.rating());
        return dto(v);
    }
    @Transactional(readOnly = true)
    public List<ReviewDto> forUser(Long userId) { return reviews.findByRevieweeIdOrderByCreatedAtDesc(userId).stream().map(this::dto).toList(); }
    private ReviewDto dto(Review v) { return new ReviewDto(v.getId(), v.getOrderId(), lookup.name(v.getReviewerId()), lookup.name(v.getRevieweeId()), v.getRating(), v.getComment(), v.getCreatedAt()); }
}
