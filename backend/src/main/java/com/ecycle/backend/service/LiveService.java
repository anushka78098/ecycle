package com.ecycle.backend.service;

import com.ecycle.shared.Dtos.LiveEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/** Pushes real-time events to one signed-in user over STOMP (/user/queue/events). Best effort: a failed push never fails the business action. */
@Service @RequiredArgsConstructor
public class LiveService {
    private static final Logger log = LoggerFactory.getLogger(LiveService.class);
    public static final String DEST = "/queue/events";
    private final SimpMessagingTemplate template;

    public void push(Long userId, String type, Object payload) {
        if (userId == null) return;
        try { template.convertAndSendToUser(String.valueOf(userId), DEST, new LiveEvent(type, payload, Instant.now())); }
        catch (RuntimeException e) { log.warn("Live push to user {} failed: {}", userId, e.getMessage()); }
    }
}
