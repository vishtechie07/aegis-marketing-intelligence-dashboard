package com.aegis.agent;

import com.aegis.config.ApiKeyNotConfiguredException;
import com.aegis.config.DynamicChatClientProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrategistAgent {

    private final DynamicChatClientProvider provider;

    public record StrategistResult(int threatLevel, String summary, String strategicAdvice) {}

    private static final String PROMPT = """
            You are a strategic business intelligence analyst advising a B2B SaaS company.
            Analyze this competitor news and respond with a JSON object in this exact format:
            {{
              "threatLevel": integer 1-10 (10 is existential threat),
              "summary": one sentence neutral summary of what happened,
              "strategicAdvice": one sentence action-oriented advice starting with a verb
            }}
            
            Threat scoring guide:
            1-3: Low (generic news, no direct impact)
            4-6: Medium (market expansion, hiring, product updates)
            7-8: High (direct competitor to your core market, major funding)
            9-10: Critical (acquisition of key partner, direct price war, entering your exact niche)
            
            Competitor: {competitor}
            Category: {category}
            Title: {title}
            Content: {content}
            
            JSON Response:""";

    @SuppressWarnings("null")
    public StrategistResult analyze(String competitor, String category, String title, String content) {
        try {
            var converter = new BeanOutputConverter<>(StrategistResult.class);
            String safeContent = content != null ? content.substring(0, Math.min(content.length(), 800)) : "";
            Object raw = provider.get().prompt()
                    .user(u -> u.text(PROMPT)
                            .param("competitor", competitor != null ? competitor : "")
                            .param("category", category != null ? category : "")
                            .param("title", title != null ? title : "")
                            .param("content", safeContent))
                    .call()
                    .content();
            if (raw == null) return fallback();
            String response = raw.toString();
            String json = extractJson(response);
            return converter.convert(json);
        } catch (ApiKeyNotConfiguredException e) {
            log.info("[Strategist] skipped — key not configured");
            return fallback();
        } catch (Exception e) {
            log.warn("[Strategist] analysis failed: {}", e.getMessage());
            return fallback();
        }
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        return (start >= 0 && end > start) ? response.substring(start, end + 1) : response;
    }

    private StrategistResult fallback() {
        return new StrategistResult(5, "Analysis unavailable.", "Monitor this competitor closely for further developments.");
    }
}
