package com.ecycle.backend.service;

import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.mongo.NotificationDoc;
import com.ecycle.backend.mongo.NotificationMongoRepository;
import com.ecycle.backend.repository.UserRepository;
import com.ecycle.shared.Dtos.NotificationDto;
import com.ecycle.shared.Enums.Role;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notifications, stored in MongoDB and pushed live over WebSocket. Creating a notification is a best-effort side effect: if MongoDB is
 * unavailable the business transaction (order, pickup...) in PostgreSQL still succeeds and the failure is logged.
 */
@Service @RequiredArgsConstructor
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationMongoRepository repo; private final UserRepository users; private final EmailService email; private final LiveService live;

    public void notify(Long userId, String title, String body, String type) {
        if (userId == null) return;
        try {
            NotificationDoc n = new NotificationDoc(); n.setUserId(userId); n.setTitle(title); n.setBody(body); n.setType(type);
            repo.save(n);
            live.push(userId, "NOTIFICATION", dto(n));
        } catch (RuntimeException e) { log.warn("Could not store notification for user {}: {}", userId, e.getMessage()); }
        try { users.findById(userId).ifPresent(u -> email.send(u.getEmail(), "[E-CYCLE] " + title, body)); }
        catch (RuntimeException e) { log.warn("Notification email failed: {}", e.getMessage()); }
    }
    public void notifyAll(Collection<Long> ids, String title, String body, String type) { ids.stream().distinct().forEach(i -> notify(i, title, body, type)); }
    @Transactional(readOnly = true)
    public void notifyRole(Role role, String title, String body, String type) { users.findByRolesName(role.name()).forEach(u -> notify(u.getId(), title, body, type)); }

    private static NotificationDto dto(NotificationDoc n) { return new NotificationDto(n.getId(), n.getTitle(), n.getBody(), n.getType(), n.isRead(), n.getCreatedAt()); }
    public List<NotificationDto> mine(Long uid) { return repo.findTop50ByUserIdOrderByCreatedAtDesc(uid).stream().map(NotificationService::dto).toList(); }
    public long unread(Long uid) { return repo.countByUserIdAndReadFalse(uid); }
    public void markRead(Long uid, String id) {
        NotificationDoc n = repo.findById(id).orElseThrow(() -> ApiException.notFound("Notification not found"));
        if (!n.getUserId().equals(uid)) throw ApiException.forbidden("Not your notification");
        n.setRead(true); repo.save(n);
    }
    public void markAllRead(Long uid) { var l = repo.findByUserIdAndReadFalse(uid); l.forEach(n -> n.setRead(true)); repo.saveAll(l); }
}
