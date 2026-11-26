package com.ecycle.backend.service;

/** Spring application event published for every audit-trail entry, so feature modules (eco-points, certificates) can react without coupling to the originating service. */
public record AuditRecorded(Long actorId, String action, String entityType, Long entityId, String detail) {}
