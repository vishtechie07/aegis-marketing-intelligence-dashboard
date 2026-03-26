package com.aegis.agent;

import com.aegis.config.ApiKeyNotConfiguredException;
import com.aegis.config.DynamicChatClientProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketAnalystAgent {

    private final DynamicChatClientProvider provider;

    public enum Category {
        PRODUCT_LAUNCH, HIRING, FINANCIAL_MOVE, PARTNERSHIP, LEGAL, LEADERSHIP_CHANGE, OTHER
    }

    private static final String PROMPT = """
            You are a B2B market intelligence analyst.
            Categorize the following competitor news into exactly ONE of these categories:
            PRODUCT_LAUNCH, HIRING, FINANCIAL_MOVE, PARTNERSHIP, LEGAL, LEADERSHIP_CHANGE, OTHER
            
            Respond with only the category name, nothing else.
            
            Competitor: {competitor}
            Title: {title}
            Content: {content}
            
            Category:""";

    @SuppressWarnings("null")
    public Category categorize(String competitor, String title, String content) {
        try {
            String safeContent = content != null ? content.substring(0, Math.min(content.length(), 500)) : "";
            Object raw = provider.get().prompt()
                    .user(u -> u.text(PROMPT)
                            .param("competitor", competitor != null ? competitor : "")
                            .param("title", title != null ? title : "")
                            .param("content", safeContent))
                    .call()
                    .content();
            if (raw == null) return Category.OTHER;
            String response = raw.toString();
            return Category.valueOf(response.trim().toUpperCase().replaceAll("[^A-Z_]", ""));
        } catch (ApiKeyNotConfiguredException e) {
            log.info("[MarketAnalyst] skipped — key not configured");
            return Category.OTHER;
        } catch (Exception e) {
            log.warn("[MarketAnalyst] categorization failed: {}", e.getMessage());
            return Category.OTHER;
        }
    }
}
