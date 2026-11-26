package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.entity.Listing;
import com.ecycle.backend.entity.ProductLifecycle;
import com.ecycle.backend.repository.ProductLifecycleRepository;
import com.ecycle.backend.repository.PurchaseOrderRepository;
import com.ecycle.shared.Dtos.LifecycleDto;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class LifecycleService {
    private final ProductLifecycleRepository repo; private final Lookup lookup; private final EnvironmentalService env;
    private final PurchaseOrderRepository orders;

    @Transactional
    public void record(Long listingId, LifecycleStage stage, Pathway pathway, Long actorId, String note) {
        ProductLifecycle p = new ProductLifecycle();
        p.setListingId(listingId); p.setStage(stage); p.setPathway(pathway); p.setActorId(actorId); p.setNote(note);
        repo.save(p);
    }

    @Transactional(readOnly = true)
    public List<LifecycleDto> timeline(Long listingId, AuthUser viewer) {
        return repo.findByListingIdOrderByIdAsc(listingId).stream()
                .map(p -> new LifecycleDto(p.getStage(), p.getPathway(), lookup.label(p.getActorId(), viewer), p.getNote(), p.getCreatedAt())).toList();
    }

    /** Records a post-delivery outcome (reused / repaired / refurbished / recycled) and, the first time, its environmental impact. */
    @Transactional
    public LifecycleStage recordOutcome(Listing l, Pathway pathway, Long actorId, String note, BigDecimal measuredWeightKg) {
        LifecycleStage stage = switch (pathway) {
            case REUSE -> LifecycleStage.REUSED; case REPAIR -> LifecycleStage.REPAIRED; case REFURBISH -> LifecycleStage.REFURBISHED;
            case RECYCLE -> LifecycleStage.RECYCLED; case DISPOSE -> LifecycleStage.DISPOSED;
        };
        record(l.getId(), stage, pathway, actorId, note);
        Long orderId = orders.findFirstByListingIdAndStatusInOrderByIdDesc(l.getId(), List.of(OrderStatus.DELIVERED)).map(o -> o.getId()).orElse(null);
        env.compute(l, orderId, pathway, actorId, measuredWeightKg);
        return stage;
    }
}
