package com.aegis.harvester;

import com.aegis.service.CompetitorService;
import com.aegis.service.HarvestActivityService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GdeltHarvester {

    private final WebClient webClient;
    private final HarvesterSupport support;
    private final CompetitorService competitorService;
    private final HarvestActivityService harvestActivity;

    private static final DateTimeFormatter GDELT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    @Scheduled(cron = "${aegis.sources.gdelt.cron:0 */15 * * * *}")
    public void harvest() {
        List<String> competitors = competitorService.getNames();
        competitors.forEach(this::fetchForCompetitor);
        harvestActivity.record("GDELT");
    }

    private void fetchForCompetitor(String competitor) {
        webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https").host("api.gdeltproject.org").path("/api/v2/doc/doc")
                        .queryParam("query", competitor + " sourcelang:english")
                        .queryParam("mode", "artlist")
                        .queryParam("format", "json")
                        .queryParam("maxrecords", "10")
                        .queryParam("sort", "DateDesc")
                        .build())
                .retrieve()
                .bodyToMono(GdeltResponse.class)
                .doOnError(e -> log.warn("[GDELT] Error for {}: {}", competitor, e.getMessage()))
                .onErrorComplete()
                .subscribe(resp -> {
                    if (resp == null || resp.articles() == null) return;
                    int count = 0;
                    for (GdeltResponse.Article a : resp.articles()) {
                        OffsetDateTime published = parseGdeltDate(a.seendate());
                        if (support.saveAndDispatch(competitor, a.title(), null, a.url(), published, "GDELT")) {
                            count++;
                        }
                    }
                    if (count > 0) log.info("[GDELT] {} new articles for {}", count, competitor);
                });
    }

    private OffsetDateTime parseGdeltDate(String seendate) {
        try {
            if (seendate == null) return OffsetDateTime.now(ZoneOffset.UTC);
            return OffsetDateTime.parse(seendate, GDELT_DATE.withZone(ZoneOffset.UTC));
        } catch (Exception e) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GdeltResponse(List<Article> articles) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Article(String url, String title, String seendate, String domain) {}
    }
}
