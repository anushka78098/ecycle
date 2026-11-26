package com.ecycle.backend.mongo;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/** MongoDB repository (see docs/ARCHITECTURE.md: chat, notifications and chatbot history live in MongoDB). */
public interface ChatMongoRepository extends MongoRepository<ChatMessageDoc, String> {
    List<ChatMessageDoc> findByListingIdAndPairKeyOrderByCreatedAtAsc(Long listingId, String pairKey);
    List<ChatMessageDoc> findTop1000BySenderIdOrRecipientIdOrderByCreatedAtDesc(Long sender, Long recipient);
}
