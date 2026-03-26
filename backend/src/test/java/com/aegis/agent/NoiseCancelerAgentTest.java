package com.aegis.agent;

import com.aegis.TestChatClientHelper;
import com.aegis.config.ApiKeyNotConfiguredException;
import com.aegis.config.DynamicChatClientProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoiseCancelerAgentTest {

    @Mock DynamicChatClientProvider provider;

    NoiseCancelerAgent agent;

    @BeforeEach
    void setUp() {
        agent = new NoiseCancelerAgent(provider);
    }

    @Test
    void isRelevant_returnsTrueForRelevantResponse() {
        when(provider.get()).thenReturn(TestChatClientHelper.fakeChatClient("RELEVANT"));
        assertThat(agent.isRelevant("OpenAI", "GPT-5 Released", "article content")).isTrue();
    }

    @Test
    void isRelevant_returnsFalseForNoiseResponse() {
        when(provider.get()).thenReturn(TestChatClientHelper.fakeChatClient("NOISE"));
        assertThat(agent.isRelevant("Acme", "CEO mentions company values", "pr content")).isFalse();
    }

    @Test
    void isRelevant_returnsTrueWhenKeyNotConfigured() {
        when(provider.get()).thenThrow(new ApiKeyNotConfiguredException("No key"));
        assertThat(agent.isRelevant("Acme", "Any title", "any content")).isTrue();
    }

    @Test
    void isRelevant_returnsTrueOnAiException() {
        when(provider.get()).thenReturn(TestChatClientHelper.throwingChatClient(new RuntimeException("timeout")));
        assertThat(agent.isRelevant("Acme", "Any title", "any content")).isTrue();
    }

    @Test
    void isRelevant_handlesNullContent() {
        when(provider.get()).thenReturn(TestChatClientHelper.fakeChatClient("RELEVANT"));
        assertThat(agent.isRelevant("Acme", "Some title", null)).isTrue();
    }
}
