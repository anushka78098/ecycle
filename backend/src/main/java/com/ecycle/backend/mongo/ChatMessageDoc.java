package com.ecycle.backend.mongo;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** Listing-scoped buyer/seller chat message (MongoDB). pairKey = "minUserId:maxUserId" so a two-party thread is a single indexed equality lookup. */
@Document("chat_messages") @CompoundIndex(name = "thread", def = "{'listingId': 1, 'pairKey': 1, 'createdAt': 1}")
@Getter @Setter
public class ChatMessageDoc {
    @Id private String id;
    private Long listingId;
    private Long senderId;
    private Long recipientId;
    private String pairKey;
    private String body;
    private Instant createdAt = Instant.now();

    public static String pairKey(Long a, Long b) { return Math.min(a, b) + ":" + Math.max(a, b); }
}
