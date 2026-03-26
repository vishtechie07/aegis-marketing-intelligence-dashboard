package com.aegis.service;

import com.aegis.dto.InsightEvent;
import com.aegis.entity.AgentInsight;
import com.aegis.repository.AgentInsightRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InsightService {

    private static final int REPLAY_BUFFER = 250;

    private final AgentInsightRepository insightRepository;
    private final CompetitorService competitorService;

    private final Sinks.Many<InsightEvent> sink = Sinks.many().replay().limit(REPLAY_BUFFER);

    @PostConstruct
    void init() {
        log.info("InsightService SSE sink initialized");
    }

    public void publish(InsightEvent event) {
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn("Failed to emit SSE event for news {}: {}", event.newsId(), result);
        }
    }

    public Flux<InsightEvent> stream() {
        return sink.asFlux();
    }

    public List<InsightEvent> getLatest(int limit) {
        return insightRepository.findLatestWithNews(PageRequest.of(0, Math.min(limit, 500))).stream()
                .map(this::toEvent)
                .toList();
    }

    public List<InsightEvent> getLatestPerCompetitor(int limitPerCompetitor) {
        int cap = Math.min(limitPerCompetitor, 100);
        List<InsightEvent> merged = new ArrayList<>();
        for (String competitor : competitorService.getNames()) {
            List<AgentInsight> page = insightRepository.findLatestWithNewsByCompetitor(competitor, PageRequest.of(0, cap));
            page.stream().map(this::toEvent).forEach(merged::add);
        }
        merged.sort(Comparator.comparing(InsightEvent::processedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return merged;
    }

    public List<InsightEvent> getHighThreat(int minLevel) {
        return insightRepository.findHighThreat(minLevel).stream()
                .map(this::toEvent)
                .toList();
    }

    public InsightEvent toEvent(AgentInsight insight) {
        return new InsightEvent(
                insight.getId(),
                insight.getNews().getId(),
                insight.getNews().getCompetitorName(),
                insight.getNews().getTitle(),
                insight.getNews().getSourceUrl(),
                insight.getNews().getSourceType(),
                insight.getAgentName(),
                insight.getCategory(),
                insight.getThreatLevel(),
                insight.getSummary(),
                insight.getStrategicAdvice(),
                insight.getNews().getPublishedAt(),
                insight.getProcessedAt()
        );
    }
}
