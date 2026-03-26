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
class StrategistAgentTest {

    @Mock DynamicChatClientProvider provider;

    StrategistAgent agent;

    static final String VALID_JSON = """
            {"threatLevel": 8, "summary": "OpenAI released a flagship model.", "strategicAdvice": "Accelerate your roadmap to ship before market saturation."}
            """;

    @BeforeEach
    void setUp() {
        agent = new StrategistAgent(provider);
    }

    @Test
    void analyze_parsesValidJson() {
        when(provider.get()).thenReturn(TestChatClientHelper.fakeChatClient(VALID_JSON));
        StrategistAgent.StrategistResult result = agent.analyze("OpenAI", "PRODUCT_LAUNCH", "GPT-5 released", "content");

        assertThat(result.threatLevel()).isEqualTo(8);
        assertThat(result.summary()).isEqualTo("OpenAI released a flagship model.");
        assertThat(result.strategicAdvice()).contains("Accelerate");
    }

    @Test
    void analyze_handlesJsonWrappedInText() {
        String wrapped = "Here is my analysis:\n" + VALID_JSON + "\nHope that helps.";
        when(provider.get()).thenReturn(TestChatClientHelper.fakeChatClient(wrapped));
        StrategistAgent.StrategistResult result = agent.analyze("OpenAI", "PRODUCT_LAUNCH", "title", "content");

        assertThat(result.threatLevel()).isEqualTo(8);
    }

    @Test
    void analyze_returnsFallbackOnNullContent() {
        when(provider.get()).thenReturn(TestChatClientHelper.fakeChatClient(null));
        StrategistAgent.StrategistResult result = agent.analyze("Acme", "OTHER", "title", "content");

        assertThat(result.threatLevel()).isEqualTo(5);
        assertThat(result.summary()).isEqualTo("Analysis unavailable.");
    }

    @Test
    void analyze_returnsFallbackWhenKeyNotConfigured() {
        when(provider.get()).thenThrow(new ApiKeyNotConfiguredException("No key"));
        StrategistAgent.StrategistResult result = agent.analyze("Acme", "OTHER", "title", "content");

        assertThat(result.threatLevel()).isEqualTo(5);
    }

    @Test
    void analyze_returnsFallbackOnAiException() {
        when(provider.get()).thenReturn(TestChatClientHelper.throwingChatClient(new RuntimeException("rate limit")));
        StrategistAgent.StrategistResult result = agent.analyze("Acme", "OTHER", "title", "content");

        assertThat(result.strategicAdvice()).contains("Monitor");
    }

    @Test
    void analyze_handlesNullContent() {
        when(provider.get()).thenReturn(TestChatClientHelper.fakeChatClient(VALID_JSON));
        StrategistAgent.StrategistResult result = agent.analyze("Acme", "OTHER", "title", null);

        assertThat(result.threatLevel()).isEqualTo(8);
    }

    @Test
    void analyze_threatLevelBoundsAreRespected() {
        String lowThreat = """
                {"threatLevel": 1, "summary": "Minor blog post.", "strategicAdvice": "Monitor quarterly."}
                """;
        when(provider.get()).thenReturn(TestChatClientHelper.fakeChatClient(lowThreat));
        StrategistAgent.StrategistResult result = agent.analyze("Acme", "OTHER", "title", "content");

        assertThat(result.threatLevel()).isBetween(1, 10);
    }
}
