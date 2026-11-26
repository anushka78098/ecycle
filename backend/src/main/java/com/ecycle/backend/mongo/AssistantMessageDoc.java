package com.ecycle.backend.mongo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** Chatbot transcript line shown in the UI (role USER or ASSISTANT). */
@Document("assistant_messages") @CompoundIndex(name = "conv_created", def = "{'conversationId': 1, 'createdAt': 1}")
@Getter @Setter
public class AssistantMessageDoc {
    @Id private String id;
    private String conversationId;
    private Long userId;
    private String role;
    private String content;
    private String mode;
    private List<String> toolsUsed = new ArrayList<>();
    private Instant createdAt = Instant.now();
}
