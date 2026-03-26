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
class MarketAnalystAgentTest {

    @Mock DynamicChatClientProvider provider;

    MarketAnalystAgent agent;

    @BeforeEach
    void setUp() {
        agent = new MarketAnalystAgent(provider);
    }

    @Test
    void categorize_returnsProductLaunch() {
        when(provider.get()).thenReturn(TestChatClientHelper.fakeChatClient("PRODUCT_LAUNCH"));
        assertThat(agent.categorize("OpenAI", "GPT-5 launched", "details"))
                .isEqualTo(MarketAnalystAgent.Category.PRODUCT_LAUNCH);
    }

    @Test
    void categorize_returnsHiring() {
        when(provider.get()).thenReturn(TestChatClientHelper.fakeChatClient("HIRING"));
        assertThat(agent.categorize("Google", "Google hiring 500 engineers", "details"))
                .isEqualTo(MarketAnalystAgent.Category.HIRING);
    }

    @Test
    void categorize_returnsOtherWhenResponseUnrecognised() {
        when(provider.get()).thenReturn(TestChatClientHelper.fakeChatClient("gibberish text"));
        assertThat(agent.categorize("Acme", "title", "content"))
                .isEqualTo(MarketAnalystAgent.Category.OTHER);
    }

    @Test
    void categorize_returnsOtherWhenKeyNotConfigured() {
        when(provider.get()).thenThrow(new ApiKeyNotConfiguredException("No key"));
        assertThat(agent.categorize("Acme", "title", "content"))
                .isEqualTo(MarketAnalystAgent.Category.OTHER);
    }

    @Test
    void categorize_returnsOtherOnAiException() {
        when(provider.get()).thenReturn(TestChatClientHelper.throwingChatClient(new RuntimeException("503")));
        assertThat(agent.categorize("Acme", "title", "content"))
                .isEqualTo(MarketAnalystAgent.Category.OTHER);
    }

    @Test
    void categorize_handlesAllCategories() {
        for (MarketAnalystAgent.Category expected : MarketAnalystAgent.Category.values()) {
            when(provider.get()).thenReturn(TestChatClientHelper.fakeChatClient(expected.name()));
            assertThat(agent.categorize("Acme", "t", "c")).isEqualTo(expected);
        }
    }
}
