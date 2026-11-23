package com.ecycle.backend.service;

import com.ecycle.backend.entity.SanitizationRecord;
import com.ecycle.backend.repository.SanitizationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Connects the existing modules to the new ones through the audit trail, so environmental-impact, sanitization and passport code did not need to be rewritten. */
@Component @RequiredArgsConstructor
public class DomainEventBridge {
    private static final Logger log = LoggerFactory.getLogger(DomainEventBridge.class);
    private final EcoService eco; private final CertificateService certificates; private final SanitizationRecordRepository records;

    @EventListener
    public void on(AuditRecorded e) {
        switch (e.action()) {
            case "IMPACT_RECORDED" -> { try { eco.onImpact(e.entityId()); } catch (RuntimeException ex) { log.warn("Eco-points for impact {} failed: {}", e.entityId(), ex.getMessage()); } }
            case "SANITIZATION_VERIFIED" -> {
                if (e.detail() == null) return;
                certificates.issueForSanitization(e.detail(), e.actorId());   // failures propagate: verification and certificate are one atomic step
                SanitizationRecord r = records.findByCertificateNo(e.detail()).orElse(null);
                if (r != null) { try { eco.onSanitizationVerified(r.getSubmittedBy(), r.getId(), e.detail()); } catch (RuntimeException ex) { log.warn("Eco-points for sanitization failed: {}", ex.getMessage()); } }
            }
            default -> { }
        }
    }
}
