package com.ecycle.backend.assistant;

import com.ecycle.backend.mongo.AssistantConversationDoc;
import com.ecycle.backend.mongo.AssistantConversationRepository;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** LangChain4j chat memory persisted in MongoDB (one document per conversation), so the model keeps context across requests and restarts. */
@Component @RequiredArgsConstructor
public class MongoChatMemoryStore implements ChatMemoryStore {
    private final AssistantConversationRepository repo;

    @Override public List<ChatMessage> getMessages(Object memoryId) {
        return repo.findById(String.valueOf(memoryId)).map(AssistantConversationDoc::getMemoryJson).filter(j -> j != null && !j.isBlank()).map(ChatMessageDeserializer::messagesFromJson).orElseGet(List::of);
    }
    @Override public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        repo.findById(String.valueOf(memoryId)).ifPresent(d -> { d.setMemoryJson(ChatMessageSerializer.messagesToJson(messages)); d.setUpdatedAt(Instant.now()); repo.save(d); });
    }
    @Override public void deleteMessages(Object memoryId) {
        repo.findById(String.valueOf(memoryId)).ifPresent(d -> { d.setMemoryJson(null); repo.save(d); });
    }
}
