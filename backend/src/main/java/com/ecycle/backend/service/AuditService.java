package com.ecycle.backend.service;

import com.ecycle.backend.entity.AuditLog;
import com.ecycle.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes to the append-only audit trail (enforced by a database trigger). Joins the caller's transaction so audit and change commit together.
 * Each entry is also published as an {@link AuditRecorded} event (delivered synchronously, inside the same transaction).
 */
@Service @RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repo; private final ApplicationEventPublisher events;
    @Transactional(propagation = Propagation.REQUIRED)
    public void log(Long actorId, String action, String entityType, Long entityId, String detail) {
        AuditLog a = new AuditLog();
        a.setActorId(actorId); a.setAction(action); a.setEntityType(entityType); a.setEntityId(entityId);
        a.setDetail(detail == null ? null : (detail.length() > 3900 ? detail.substring(0, 3900) : detail));
        repo.save(a);
        events.publishEvent(new AuditRecorded(actorId, action, entityType, entityId, a.getDetail()));
    }
}
