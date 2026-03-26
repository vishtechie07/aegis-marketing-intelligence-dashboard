package com.aegis.harvester;

import com.aegis.service.CompetitorService;
import com.aegis.service.HarvestActivityService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class HackerNewsHarvester {

    private final WebClient webClient;
    private final HarvesterSupport support;
    private final CompetitorService competitorService;
    private final HarvestActivityService harvestActivity;

    @Scheduled(cron = "${aegis.sources.hackernews.cron:0 */20 * * * *}")
    public void harvest() {
        List<String> competitors = competitorService.getNames();
        competitors.forEach(this::fetchForCompetitor);
        harvestActivity.record("HACKERNEWS");
    }

    private void fetchForCompetitor(String competitor) {
        webClient.get()
                .uri("https://hn.algolia.com/api/v1/search_by_date?query={q}&tags=story&hitsPerPage=10",
                        competitor)
                .retrieve()
                .bodyToMono(HnResponse.class)
                .doOnError(e -> log.warn("[HN] Error for {}: {}", competitor, e.getMessage()))
                .onErrorComplete()
                .subscribe(resp -> {
                    if (resp == null || resp.hits() == null) return;
                    int count = 0;
                    for (HnResponse.Hit hit : resp.hits()) {
                        if (hit.title() == null) continue;
                        String url = hit.url() != null ? hit.url()
                                : "https://news.ycombinator.com/item?id=" + hit.objectId();
                        String content = hit.storyText();
                        OffsetDateTime published = hit.createdAt() != null
                                ? OffsetDateTime.parse(hit.createdAt())
                                : OffsetDateTime.now(ZoneOffset.UTC);
                        if (support.saveAndDispatch(competitor, hit.title(), content, url, published, "HACKERNEWS")) {
                            count++;
                        }
                    }
                    if (count > 0) log.info("[HN] {} new stories for {}", count, competitor);
                });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HnResponse(List<Hit> hits) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Hit(
                String title,
                String url,
                @JsonProperty("story_text") String storyText,
                @JsonProperty("created_at") String createdAt,
                @JsonProperty("objectID") String objectId
        ) {}
    }
}
