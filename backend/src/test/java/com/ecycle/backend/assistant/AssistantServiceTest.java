package com.ecycle.backend.assistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ecycle.backend.config.AppProps;
import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.mongo.*;
import com.ecycle.shared.Dtos.AssistantRequest;
import com.ecycle.shared.Enums.Role;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssistantServiceTest {
    @Test
    void savesConversationTimestampWithoutOverwritingUpdatedChatMemory() {
        AssistantActions actions = mock(AssistantActions.class);
        MongoChatMemoryStore memory = mock(MongoChatMemoryStore.class);
        AppProps props = mock(AppProps.class);
        AssistantConversationRepository conversations = mock(AssistantConversationRepository.class);
        AssistantMessageRepository messages = mock(AssistantMessageRepository.class);
        AssistantPendingActionRepository pending = mock(AssistantPendingActionRepository.class);
        Instant previousUpdate = Instant.parse("2024-01-01T00:00:00Z");
        AssistantConversationDoc conversation = new AssistantConversationDoc();
        conversation.setId("conversation-1");
        conversation.setUserId(7L);
        conversation.setMemoryJson("{\"recent\":\"turn\"}");
        conversation.setUpdatedAt(previousUpdate);
        when(conversations.findById("conversation-1")).thenReturn(Optional.of(conversation));
        when(conversations.save(any())).thenAnswer(call -> call.getArgument(0));
        when(props.getAssistantPerMinute()).thenReturn(15);

        AssistantService service = new AssistantService(new AssistantConfig.Llm(null, "rules"), actions, memory, props,
                conversations, messages, pending);
        AuthUser user = new AuthUser(7L, "buyer@example.test", Set.of(Role.BUYER));

        service.chat(user, new AssistantRequest("conversation-1", "Tell me about batteries"));

        ArgumentCaptor<AssistantConversationDoc> saved = ArgumentCaptor.forClass(AssistantConversationDoc.class);
        verify(conversations).save(saved.capture());
        assertTrue(saved.getValue().getUpdatedAt().isAfter(previousUpdate));
        assertEquals("{\"recent\":\"turn\"}", saved.getValue().getMemoryJson());
    }
}
