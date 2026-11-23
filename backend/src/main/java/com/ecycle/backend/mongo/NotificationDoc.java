package com.ecycle.backend.mongo;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** In-app notification (MongoDB). */
@Document("notifications") @CompoundIndex(name = "user_created", def = "{'userId': 1, 'createdAt': -1}")
@Getter @Setter
public class NotificationDoc {
    @Id private String id;
    private Long userId;
    private String title;
    private String body;
    private String type;
    private boolean read = false;
    private Instant createdAt = Instant.now();
}
