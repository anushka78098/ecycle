package com.ecycle.backend.assistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ecycle.backend.config.AppProps;
import org.junit.jupiter.api.Test;

class AssistantConfigTest {
    @Test
    void geminiWithoutApiKeyUsesVisibleRuleBasedMode() {
        AppProps props = mock(AppProps.class);
        when(props.getAssistantProvider()).thenReturn("gemini");
        when(props.getAssistantApiKey()).thenReturn("");
        when(props.getAssistantBaseUrl()).thenReturn("");

        AssistantConfig.Llm llm = new AssistantConfig().llm(props);

        assertFalse(llm.enabled());
        assertTrue(llm.description().contains("Gemini API key missing"));
    }

    @Test
    void geminiUsesFlashLiteWhenModelIsNotConfigured() {
        AppProps props = mock(AppProps.class);
        when(props.getAssistantProvider()).thenReturn("gemini");
        when(props.getAssistantApiKey()).thenReturn("test-key");
        when(props.getAssistantBaseUrl()).thenReturn("");
        when(props.getAssistantModel()).thenReturn("");
        when(props.getAssistantTimeoutSeconds()).thenReturn(60);

        AssistantConfig.Llm llm = new AssistantConfig().llm(props);

        assertTrue(llm.enabled());
        assertEquals("gemini-3.1-flash-lite", llm.description());
    }
}
