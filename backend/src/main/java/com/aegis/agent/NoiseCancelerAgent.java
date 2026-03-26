package com.aegis.agent;

import com.aegis.config.ApiKeyNotConfiguredException;
import com.aegis.config.DynamicChatClientProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoiseCancelerAgent {

    private final DynamicChatClientProvider provider;

    private static final String PROMPT = """
            You are a B2B market intelligence noise filter.
            Determine if the following article contains GENUINE business intelligence about a competitor
            (product launches, acquisitions, earnings, key hires, partnerships, lawsuits, or market moves).
            
            Respond with exactly one word: RELEVANT or NOISE.
            
            NOISE examples: press releases, awards, generic industry roundups, CEO quotes about culture.
            RELEVANT examples: new product announcements, layoffs, acquisitions, earnings beats/misses, funding rounds.
            
            Competitor: {competitor}
            Title: {title}
            Content: {content}
            
            Response:""";

    @SuppressWarnings("null")
    public boolean isRelevant(String competitor, String title, String content) {
        try {
            String safeContent = content != null ? content.substring(0, Math.min(content.length(), 500)) : "";
            Object raw = provider.get().prompt()
                    .user(u -> u.text(PROMPT)
                            .param("competitor", competitor != null ? competitor : "")
                            .param("title", title != null ? title : "")
                            .param("content", safeContent))
                    .call()
                    .content();
            if (raw == null) return true;
            String response = raw.toString();
            return response.trim().toUpperCase().contains("RELEVANT");
        } catch (ApiKeyNotConfiguredException e) {
            log.info("[NoiseCanceler] skipped — key not configured");
            return true;
        } catch (Exception e) {
            log.warn("[NoiseCanceler] failed, defaulting to relevant: {}", e.getMessage());
            return true;
        }
    }
}
