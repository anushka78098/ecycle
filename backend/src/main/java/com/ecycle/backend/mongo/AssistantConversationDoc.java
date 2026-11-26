package com.ecycle.backend.mongo;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** One chatbot conversation. memoryJson is the serialized LangChain4j chat memory (rolling window); the readable transcript is in AssistantMessageDoc. */
@Document("assistant_conversations")
@Getter @Setter
public class AssistantConversationDoc {
    @Id private String id;
    @Indexed private Long userId;
    private String title;
    private String memoryJson;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
