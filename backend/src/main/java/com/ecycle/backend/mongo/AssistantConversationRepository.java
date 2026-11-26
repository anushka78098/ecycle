package com.ecycle.backend.mongo;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/** MongoDB repository (see docs/ARCHITECTURE.md: chat, notifications and chatbot history live in MongoDB). */
public interface AssistantConversationRepository extends MongoRepository<AssistantConversationDoc, String> {
    List<AssistantConversationDoc> findTop30ByUserIdOrderByUpdatedAtDesc(Long userId);
}
