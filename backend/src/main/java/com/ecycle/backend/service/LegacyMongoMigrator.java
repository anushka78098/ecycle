package com.ecycle.backend.service;

import com.ecycle.backend.mongo.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time, idempotent copy of chat messages and notifications from the legacy PostgreSQL tables (V1) into MongoDB. Rows are only copied while the Mongo collection is empty,
 * and the legacy tables are left untouched (never dropped automatically) so the migration can be re-checked or rolled back.
 */
@Component @RequiredArgsConstructor @Order(1)
public class LegacyMongoMigrator implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LegacyMongoMigrator.class);
    private final JdbcTemplate jdbc; private final ChatMongoRepository chat; private final NotificationMongoRepository notes;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (chat.count() == 0) {
                List<ChatMessageDoc> docs = jdbc.query("select listing_id, sender_id, recipient_id, body, created_at from chat_messages order by id", (rs, i) -> {
                    ChatMessageDoc d = new ChatMessageDoc(); d.setListingId(rs.getLong(1)); d.setSenderId(rs.getLong(2)); d.setRecipientId(rs.getLong(3)); d.setPairKey(ChatMessageDoc.pairKey(d.getSenderId(), d.getRecipientId()));
                    d.setBody(rs.getString(4)); d.setCreatedAt(rs.getTimestamp(5).toInstant()); return d; });
                if (!docs.isEmpty()) { chat.saveAll(docs); log.info("Migrated {} chat messages from PostgreSQL to MongoDB", docs.size()); }
            }
            if (notes.count() == 0) {
                List<NotificationDoc> docs = jdbc.query("select user_id, title, body, type, read_flag, created_at from notifications order by id", (rs, i) -> {
                    NotificationDoc d = new NotificationDoc(); d.setUserId(rs.getLong(1)); d.setTitle(rs.getString(2)); d.setBody(rs.getString(3)); d.setType(rs.getString(4)); d.setRead(rs.getBoolean(5)); d.setCreatedAt(rs.getTimestamp(6).toInstant()); return d; });
                if (!docs.isEmpty()) { notes.saveAll(docs); log.info("Migrated {} notifications from PostgreSQL to MongoDB", docs.size()); }
            }
        } catch (RuntimeException e) { log.warn("Legacy chat/notification migration skipped: {}", e.getMessage()); }
    }
}
