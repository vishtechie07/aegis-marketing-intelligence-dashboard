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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecEdgarHarvester {

    private final WebClient webClient;
    private final HarvesterSupport support;
    private final CompetitorService competitorService;
    private final HarvestActivityService harvestActivity;

    private static final String FORMS = "8-K,S-1,SC 13D";

    @Scheduled(cron = "${aegis.sources.edgar.cron:0 0 */4 * * *}")
    public void harvest() {
        String startdt = LocalDate.now(ZoneOffset.UTC).minusDays(2).toString();
        List<String> competitors = competitorService.getNames();
        competitors.forEach(c -> fetchForCompetitor(c, startdt));
        harvestActivity.record("EDGAR");
    }

    private void fetchForCompetitor(String competitor, String startdt) {
        webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https").host("efts.sec.gov").path("/LATEST/search-index")
                        .queryParam("q", "\"" + competitor + "\"")
                        .queryParam("forms", FORMS)
                        .queryParam("dateRange", "custom")
                        .queryParam("startdt", startdt)
                        .build())
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(EdgarResponse.class)
                .doOnError(e -> log.warn("[EDGAR] Error for {}: {}", competitor, e.getMessage()))
                .onErrorComplete()
                .subscribe(resp -> {
                    if (resp == null || resp.hits() == null || resp.hits().hits() == null) return;
                    int count = 0;
                    for (EdgarResponse.Hit hit : resp.hits().hits()) {
                        EdgarResponse.Source src = hit.source();
                        if (src == null) continue;
                        String title = buildTitle(src);
                        String url = buildFilingUrl(hit.id());
                        OffsetDateTime published = src.fileDate() != null
                                ? LocalDate.parse(src.fileDate()).atStartOfDay().atOffset(ZoneOffset.UTC)
                                : OffsetDateTime.now(ZoneOffset.UTC);
                        String content = src.formType() + " filing: " + src.entityName();
                        if (support.saveAndDispatch(competitor, title, content, url, published, "EDGAR")) {
                            count++;
                        }
                    }
                    if (count > 0) log.info("[EDGAR] {} new filings for {}", count, competitor);
                });
    }

    private String buildTitle(EdgarResponse.Source src) {
        return String.format("[%s] %s — SEC Filing",
                src.formType() != null ? src.formType() : "Filing",
                src.entityName() != null ? src.entityName() : "Unknown Entity");
    }

    private String buildFilingUrl(String id) {
        return "https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&filenum=" + id;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EdgarResponse(Hits hits) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Hits(List<Hit> hits) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Hit(
                @JsonProperty("_id") String id,
                @JsonProperty("_source") Source source
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Source(
                @JsonProperty("entity_name") String entityName,
                @JsonProperty("file_date") String fileDate,
                @JsonProperty("form_type") String formType,
                @JsonProperty("period_of_report") String periodOfReport
        ) {}
    }
}
