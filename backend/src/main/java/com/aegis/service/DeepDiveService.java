package com.aegis.service;

import com.aegis.config.DynamicChatClientProvider;
import com.aegis.dto.DeepDiveHistoryEntry;
import com.aegis.entity.CompetitorNews;
import com.aegis.entity.DeepDiveLog;
import com.aegis.repository.CompetitorNewsRepository;
import com.aegis.repository.DeepDiveLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeepDiveService {

    private final DynamicChatClientProvider provider;
    private final CompetitorNewsRepository newsRepository;
    private final DeepDiveLogRepository deepDiveLogRepository;

    private static final String PROMPT = """
            You are a senior competitive intelligence analyst.
            A user asked: "{question}"
            
            Use the following news article about {competitor} to provide a deep-dive analysis:
            Title: {title}
            Content: {content}
            
            Return PLAIN TEXT ONLY. Do not use Markdown and do not use '*', '-', or '**'.
            Use '• ' (bullet character) for bullets.

            Output format (exact section headers; keep ordering):

            Answer:
            <one concise paragraph>

            Strategic implications:
            • <3-5 bullets>

            Recommended actions:
            • <3-5 bullets>""";

    @Transactional
    @SuppressWarnings("null")
    public String deepDive(Long newsId, String question) {
        CompetitorNews news = newsRepository.findById(newsId)
                .orElseThrow(() -> new IllegalArgumentException("News not found: " + newsId));
        String safeContent = news.getContent() != null
                ? news.getContent().substring(0, Math.min(news.getContent().length(), 1000))
                : "No content available";
        Object raw = provider.get().prompt()
                .user(u -> u.text(PROMPT)
                        .param("question", question != null ? question : "")
                        .param("competitor", news.getCompetitorName() != null ? news.getCompetitorName() : "")
                        .param("title", news.getTitle() != null ? news.getTitle() : "")
                        .param("content", safeContent))
                .call()
                .content();
        String analysis = normalizePlainText(raw != null ? raw.toString() : "");
        String q = question != null ? question : "";
        if (!q.isBlank()) {
            deepDiveLogRepository.save(DeepDiveLog.builder()
                    .newsId(newsId)
                    .question(q)
                    .analysis(analysis)
                    .build());
        }
        return analysis;
    }

    public List<DeepDiveHistoryEntry> history(Long newsId) {
        return deepDiveLogRepository.findTop20ByNewsIdOrderByCreatedAtDesc(newsId).stream()
                .map(log -> new DeepDiveHistoryEntry(
                        log.getId(),
                        log.getNewsId(),
                        log.getQuestion() != null ? log.getQuestion() : "",
                        log.getAnalysis() != null ? log.getAnalysis() : "",
                        log.getCreatedAt()))
                .toList();
    }

    private String normalizePlainText(String text) {
        if (text == null || text.isBlank()) return "";
        String t = text.replace("\r\n", "\n");
        // Remove markdown-ish headings and bold markers.
        t = t.replaceAll("(?m)^\\s*#{1,6}\\s*", "");
        t = t.replace("**", "");
        t = t.replaceAll("`{1,3}", "");
        // Normalize list markers to a single bullet character.
        t = t.replaceAll("(?m)^\\s*[-*]\\s+", "• ");
        return t.trim();
    }
}
