package com.ecycle.backend.mongo;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/** MongoDB repository (see docs/ARCHITECTURE.md: chat, notifications and chatbot history live in MongoDB). */
public interface NotificationMongoRepository extends MongoRepository<NotificationDoc, String> {
    List<NotificationDoc> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndReadFalse(Long userId);
    List<NotificationDoc> findByUserIdAndReadFalse(Long userId);
}
