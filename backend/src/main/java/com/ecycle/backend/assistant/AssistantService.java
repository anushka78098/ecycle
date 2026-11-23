package com.ecycle.backend.assistant;

import com.ecycle.backend.config.AppProps;
import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.mongo.*;
import com.ecycle.shared.Dtos.*;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class AssistantService {
    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private final AssistantConfig.Llm llm; private final AssistantActions actions; private final MongoChatMemoryStore store; private final AppProps props;
    private final AssistantConversationRepository conversations; private final AssistantMessageRepository messages; private final AssistantPendingActionRepository pending;
    private final Map<Long, Deque<Long>> recent = new ConcurrentHashMap<>();

    public AssistantReplyDto chat(AuthUser me, AssistantRequest req) {
        throttle(me.id());
        AssistantConversationDoc conv = req.conversationId() == null || req.conversationId().isBlank() ? null : conversations.findById(req.conversationId()).orElse(null);
        if (conv != null && !conv.getUserId().equals(me.id())) throw ApiException.notFound("Conversation not found");
        if (conv == null) { conv = new AssistantConversationDoc(); conv.setUserId(me.id()); conv.setTitle(cut(req.message().trim(), 60)); conv = conversations.save(conv); }
        save(conv.getId(), me.id(), "user", req.message(), null, List.of());
        EcoTools tools = new EcoTools(actions, me, conv.getId());
        String reply, mode;
        if (llm.enabled()) {
            try {
                EcoAssistant ai = AiServices.builder(EcoAssistant.class).chatLanguageModel(llm.model()).tools(tools).systemMessageProvider(id -> AssistantConfig.SYSTEM_PROMPT)
                        .chatMemoryProvider(id -> MessageWindowChatMemory.builder().id(id).maxMessages(props.getAssistantMemoryMessages()).chatMemoryStore(store).build()).build();
                reply = ai.chat(conv.getId(), req.message()); mode = "AI";
            } catch (RuntimeException e) {
                log.warn("LLM call failed, using rule-based fallback: {}", e.toString());
                reply = new RuleBasedAssistant(actions, me, tools).answer(req.message()); mode = "RULES (AI unavailable)";
            }
        } else { reply = new RuleBasedAssistant(actions, me, tools).answer(req.message()); mode = "RULES"; }
        if (reply == null || reply.isBlank()) reply = "Sorry, I could not produce an answer. Please rephrase.";
        save(conv.getId(), me.id(), "assistant", reply, mode, tools.toolsUsed());
        conv.setUpdatedAt(Instant.now()); conversations.save(conversations.findById(conv.getId()).orElse(conv)).setUpdatedAt(Instant.now());
        return new AssistantReplyDto(conv.getId(), reply, mode, tools.toolsUsed(), tools.proposals());
    }

    public List<ConversationDto> conversations(AuthUser me) { return conversations.findTop30ByUserIdOrderByUpdatedAtDesc(me.id()).stream().map(c -> new ConversationDto(c.getId(), c.getTitle(), c.getUpdatedAt())).toList(); }
    public List<AssistantMessageDto> history(AuthUser me, String id) {
        AssistantConversationDoc c = conversations.findById(id).orElseThrow(() -> ApiException.notFound("Conversation not found"));
        if (!c.getUserId().equals(me.id())) throw ApiException.notFound("Conversation not found");
        return messages.findByConversationIdOrderByCreatedAtAsc(id).stream().map(m -> new AssistantMessageDto(m.getId(), m.getRole(), m.getContent(), m.getMode(), m.getToolsUsed(), m.getCreatedAt())).toList();
    }
    public void delete(AuthUser me, String id) {
        AssistantConversationDoc c = conversations.findById(id).orElseThrow(() -> ApiException.notFound("Conversation not found"));
        if (!c.getUserId().equals(me.id())) throw ApiException.notFound("Conversation not found");
        messages.deleteAll(messages.findByConversationIdOrderByCreatedAtAsc(id)); conversations.delete(c);
    }
    public List<PendingActionDto> pendingActions(AuthUser me) {
        return pending.findByUserIdAndStatusOrderByCreatedAtDesc(me.id(), "PENDING").stream().filter(d -> d.getExpiresAt().isAfter(Instant.now())).map(AssistantActions::dto).toList();
    }
    public ActionResultDto confirm(AuthUser me, String id) { return actions.confirm(me, id); }
    public ActionResultDto cancel(AuthUser me, String id) { return actions.cancel(me, id); }
    public Map<String, Object> status() { return Map.of("mode", llm.enabled() ? "AI" : "RULES", "model", llm.description()); }

    private void save(String conv, Long uid, String role, String content, String mode, List<String> toolsUsed) {
        AssistantMessageDoc m = new AssistantMessageDoc(); m.setConversationId(conv); m.setUserId(uid); m.setRole(role); m.setContent(content); m.setMode(mode); m.setToolsUsed(new ArrayList<>(toolsUsed)); messages.save(m);
    }
    private void throttle(Long uid) {
        long now = System.currentTimeMillis(); Deque<Long> q = recent.computeIfAbsent(uid, k -> new ArrayDeque<>());
        synchronized (q) { while (!q.isEmpty() && now - q.peekFirst() > 60_000) q.pollFirst(); if (q.size() >= props.getAssistantPerMinute()) throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Too many assistant requests; wait a minute."); q.addLast(now); }
    }
    private static String cut(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }
}
