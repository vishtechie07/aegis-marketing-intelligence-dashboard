package com.aegis;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

/**
 * Builds a real ChatClient backed by a stub ChatModel that always returns the given text.
 * Avoids mocking Spring AI's complex fluent-API inner classes.
 */
public final class TestChatClientHelper {

    private TestChatClientHelper() {}

    public static ChatClient fakeChatClient(String responseText) {
        ChatModel stubModel = prompt -> {
            AssistantMessage msg = new AssistantMessage(responseText);
            Generation gen = new Generation(msg);
            return new ChatResponse(List.of(gen));
        };
        return ChatClient.builder(stubModel).build();
    }

    public static ChatClient throwingChatClient(RuntimeException ex) {
        ChatModel stubModel = prompt -> { throw ex; };
        return ChatClient.builder(stubModel).build();
    }
}
