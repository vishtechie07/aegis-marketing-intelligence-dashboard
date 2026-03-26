package com.aegis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
@SuppressWarnings("deprecation")
public class DynamicChatClientProvider {

    private static final Logger log = LoggerFactory.getLogger(DynamicChatClientProvider.class);
    private final AtomicReference<ChatClient> clientRef = new AtomicReference<ChatClient>();
    private final AtomicBoolean runtimeKeySet = new AtomicBoolean(false);

    public static final String PLACEHOLDER_KEY = "sk-placeholder-no-real-calls";

    public static boolean isPlaceholderKey(String key) {
        return key != null && PLACEHOLDER_KEY.equals(key.trim());
    }

    public DynamicChatClientProvider(
            @Value("${spring.ai.openai.api-key:}") String envKey,
            @Autowired(required = false) ChatModel autoConfiguredModel) {

        String key = envKey != null ? envKey : "";
        boolean validEnvKey = !key.isBlank() && !PLACEHOLDER_KEY.equals(key.trim());
        if (validEnvKey && autoConfiguredModel != null) {
            clientRef.set(ChatClient.builder(autoConfiguredModel).build());
            log.info("AI ChatClient initialised from environment key");
        } else {
            if (key.contains("placeholder")) {
                log.info("Placeholder API key detected - AI agents inactive until key is set via Settings");
            } else {
                log.warn("No OpenAI API key - AI features disabled until key is set via Settings");
            }
        }
    }

    public synchronized void updateKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return;
        OpenAiApi api = new OpenAiApi(apiKey);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("gpt-4o-mini")
                .temperature(0.3)
                .build();
        OpenAiChatModel model = new OpenAiChatModel(api, options);
        clientRef.set(ChatClient.builder(model).build());
        runtimeKeySet.set(true);
        log.debug("ChatClient updated with runtime key");
    }

    public ChatClient get() {
        ChatClient c = clientRef.get();
        if (c == null) throw new ApiKeyNotConfiguredException(
                "OpenAI API key not configured. Please add it via the Settings panel.");
        return c;
    }

    public boolean isConfigured() {
        return clientRef.get() != null;
    }

    public boolean isRuntimeKeySet() {
        return runtimeKeySet.get();
    }
}
