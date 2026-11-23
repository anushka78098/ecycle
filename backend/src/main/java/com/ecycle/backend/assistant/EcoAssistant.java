package com.ecycle.backend.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

/** LangChain4j AI service. The system prompt is supplied by AssistantConfig (see SYSTEM_PROMPT). */
public interface EcoAssistant {
    String chat(@MemoryId String conversationId, @UserMessage String message);
}
