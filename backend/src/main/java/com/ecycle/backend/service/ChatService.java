package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.entity.Listing;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.mongo.ChatMessageDoc;
import com.ecycle.backend.mongo.ChatMongoRepository;
import com.ecycle.backend.repository.ListingRepository;
import com.ecycle.shared.Dtos.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Listing-scoped seller/buyer messaging stored in MongoDB and delivered in real time over STOMP. One side of every conversation must be the listing's seller. */
@Service @RequiredArgsConstructor
public class ChatService {
    private final ChatMongoRepository repo; private final ListingRepository listings; private final Lookup lookup; private final NotificationService notifications; private final LiveService live;

    public ChatDto send(AuthUser me, ChatRequest r) {
        Listing l = listings.findById(r.listingId()).orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (me.id().equals(r.recipientId())) throw ApiException.badRequest("You cannot message yourself");
        boolean ok = l.getSellerId().equals(me.id()) || l.getSellerId().equals(r.recipientId());
        if (!ok) throw ApiException.forbidden("Messages must be between a buyer and the listing's seller");
        lookup.user(r.recipientId());
        ChatMessageDoc m = new ChatMessageDoc(); m.setListingId(l.getId()); m.setSenderId(me.id()); m.setRecipientId(r.recipientId());
        m.setPairKey(ChatMessageDoc.pairKey(me.id(), r.recipientId())); m.setBody(r.body().trim()); repo.save(m);
        ChatDto d = dto(m, l.getName());
        live.push(r.recipientId(), "CHAT", d); live.push(me.id(), "CHAT", d);
        notifications.notify(r.recipientId(), "New message about " + l.getName(), lookup.name(me.id()) + ": " + (r.body().length() > 120 ? r.body().substring(0, 120) + "..." : r.body()), "CHAT");
        return d;
    }
    public List<ChatDto> thread(AuthUser me, Long listingId, Long otherId) {
        String name = listings.findById(listingId).map(Listing::getName).orElse("?");
        return repo.findByListingIdAndPairKeyOrderByCreatedAtAsc(listingId, ChatMessageDoc.pairKey(me.id(), otherId)).stream().map(m -> dto(m, name)).toList();
    }
    /** Latest message of each (listing, counterpart) conversation. */
    public List<ChatDto> conversations(AuthUser me) {
        Map<String, ChatMessageDoc> latest = new LinkedHashMap<>();
        for (ChatMessageDoc m : repo.findTop1000BySenderIdOrRecipientIdOrderByCreatedAtDesc(me.id(), me.id())) {
            Long other = m.getSenderId().equals(me.id()) ? m.getRecipientId() : m.getSenderId();
            latest.putIfAbsent(m.getListingId() + ":" + other, m);
        }
        return latest.values().stream().map(m -> dto(m, listings.findById(m.getListingId()).map(Listing::getName).orElse("?"))).toList();
    }
    private ChatDto dto(ChatMessageDoc m, String listingName) {
        return new ChatDto(m.getId(), m.getListingId(), listingName, m.getSenderId(), lookup.name(m.getSenderId()), m.getRecipientId(), lookup.name(m.getRecipientId()), m.getBody(), m.getCreatedAt());
    }
}
