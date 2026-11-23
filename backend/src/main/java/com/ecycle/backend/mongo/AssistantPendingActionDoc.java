package com.ecycle.backend.mongo;

import com.ecycle.shared.Enums.PendingActionType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An action the assistant PROPOSED on the user's behalf (book / cancel a pickup, redeem a reward). It is never executed by the language model:
 * it only runs when the signed-in user confirms it through the REST API, and then under that user's own permissions.
 */
@Document("assistant_pending_actions")
@Getter @Setter
public class AssistantPendingActionDoc {
    @Id private String id;
    @Indexed private Long userId;
    private String conversationId;
    private PendingActionType type;
    private Map<String, String> params = new LinkedHashMap<>();
    private String summary;
    private String status = "PENDING";   // PENDING | CONFIRMED | CANCELLED | EXPIRED | FAILED
    private String resultMessage;
    private Instant expiresAt;
    @Indexed(expireAfterSeconds = 86400) private Instant createdAt = Instant.now();
}
