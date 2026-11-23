package com.ecycle.backend.mongo;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/** MongoDB repository (see docs/ARCHITECTURE.md: chat, notifications and chatbot history live in MongoDB). */
public interface AssistantMessageRepository extends MongoRepository<AssistantMessageDoc, String> {
    List<AssistantMessageDoc> findByConversationIdOrderByCreatedAtAsc(String conversationId);
}
