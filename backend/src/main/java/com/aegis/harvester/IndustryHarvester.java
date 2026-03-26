package com.aegis.harvester;

import com.aegis.service.CompetitorService;
import com.aegis.service.HarvestActivityService;
import com.aegis.dto.CompetitorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** World Bank + FRED macro data by country; daily. */
@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class IndustryHarvester {

    private final WebClient webClient;
    private final HarvesterSupport support;
    private final CompetitorService competitorService;
    private final HarvestActivityService harvestActivity;

    /** ISO2 by country name. */
    private static final Map<String, String> COUNTRY_ISO2 = Map.ofEntries(
            Map.entry("United States", "US"), Map.entry("United Kingdom", "GB"),
            Map.entry("China", "CN"), Map.entry("Germany", "DE"),
            Map.entry("France", "FR"), Map.entry("Japan", "JP"),
            Map.entry("India", "IN"), Map.entry("Canada", "CA"),
            Map.entry("Australia", "AU"), Map.entry("South Korea", "KR"),
            Map.entry("Israel", "IL"), Map.entry("Sweden", "SE"),
            Map.entry("Netherlands", "NL"), Map.entry("Singapore", "SG")
    );

    private static final List<String> WB_INDICATORS = List.of(
            "NY.GDP.MKTP.KD.ZG", "FP.CPI.TOTL.ZG", "SL.UEM.TOTL.ZS"
    );

    private static final Map<String, String> WB_INDICATOR_LABELS = Map.of(
            "NY.GDP.MKTP.KD.ZG", "GDP growth",
            "FP.CPI.TOTL.ZG", "Inflation rate",
            "SL.UEM.TOTL.ZS", "Unemployment rate"
    );

    private static final Set<String> MACRO_RELEVANT = Set.of(
            "finance", "agriculture", "textiles", "energy", "retail", "defence", "healthcare"
    );

    @Scheduled(cron = "${aegis.sources.industry.cron:0 0 6 * * *}")
    public void harvest() {
        harvestFredRate();
        harvestWorldBankByCountry();
        harvestActivity.record("MACRO");
    }

    private void harvestFredRate() {
        webClient.get()
                .uri("https://fred.stlouisfed.org/graph/fredgraph.csv?id=FEDFUNDS")
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.debug("[Industry] FRED error: {}", e.getMessage()))
                .onErrorComplete()
                .subscribe(csv -> {
                    if (csv == null || csv.isBlank()) return;
                    String[] lines = csv.split("\n");
                    if (lines.length < 2) return;
                    String lastLine = lines[lines.length - 1].trim();
                    String[] parts = lastLine.split(",");
                    if (parts.length < 2) return;
                    String date = parts[0].trim();
                    String rate = parts[1].trim();

                    String title = String.format("[Macro] US Federal Funds Rate: %s%% (as of %s)", rate, date);
                    String content = String.format(
                            "The Federal Reserve target rate is %s%%. " +
                            "High rates increase borrowing costs for competitors with significant debt, " +
                            "and may affect valuations for growth-stage tech companies.", rate);
                    String url = "https://fred.stlouisfed.org/series/FEDFUNDS";

                    List<CompetitorDto> relevant = competitorService.getAll().stream()
                            .filter(c -> c.industry() == null || MACRO_RELEVANT.contains(
                                    c.industry() != null ? c.industry().toLowerCase() : ""))
                            .toList();

                    for (CompetitorDto c : relevant.isEmpty() ? competitorService.getAll() : relevant) {
                        support.saveAndDispatch(c.name(), title, content, url,
                                OffsetDateTime.now(ZoneOffset.UTC), "MACRO");
                    }
                    log.info("[Industry] FRED rate {} published for {} competitors", rate, relevant.size());
                });
    }

    private void harvestWorldBankByCountry() {
        List<CompetitorDto> competitors = competitorService.getAll().stream()
                .filter(c -> c.country() != null && COUNTRY_ISO2.containsKey(c.country()))
                .toList();

        if (competitors.isEmpty()) {
            log.debug("[Industry] No competitors with mapped countries for World Bank lookup");
            return;
        }

        for (CompetitorDto competitor : competitors) {
            String iso2 = COUNTRY_ISO2.get(competitor.country());
            if (iso2 == null) continue;

            Flux.fromIterable(WB_INDICATORS)
                    .flatMap(indicator -> fetchWorldBankIndicator(iso2, indicator)
                            .map(val -> Map.entry(indicator, val)))
                    .collectList()
                    .subscribe(entries -> {
                        if (entries.isEmpty()) return;
                        StringBuilder sb = new StringBuilder();
                        for (var entry : entries) {
                            String label = WB_INDICATOR_LABELS.getOrDefault(entry.getKey(), entry.getKey());
                            sb.append(String.format("%s: %.2f%% | ", label, entry.getValue()));
                        }
                        String content = sb.toString().replaceAll("\\| $", "");
                        String title = String.format("[Macro] %s economic indicators — %s",
                                competitor.country(), content.substring(0, Math.min(80, content.length())));
                        String url = "https://data.worldbank.org/country/" + iso2;

                        support.saveAndDispatch(competitor.name(), title, content, url,
                                OffsetDateTime.now(ZoneOffset.UTC), "MACRO");
                        log.info("[Industry] World Bank data published for {} ({})", competitor.name(), competitor.country());
                    });
        }
    }

    private Mono<Double> fetchWorldBankIndicator(String iso2, String indicator) {
        String url = String.format(
                "https://api.worldbank.org/v2/country/%s/indicator/%s?format=json&mrv=1&per_page=1",
                iso2, indicator);
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(com.fasterxml.jackson.databind.JsonNode.class)
                .mapNotNull(node -> {
                    if (node == null || !node.isArray() || node.size() < 2) return null;
                    com.fasterxml.jackson.databind.JsonNode dataArray = node.get(1);
                    if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) return null;
                    com.fasterxml.jackson.databind.JsonNode first = dataArray.get(0);
                    if (first == null || first.get("value") == null || first.get("value").isNull()) return null;
                    return first.get("value").asDouble();
                })
                .onErrorResume(e -> Mono.empty());
    }
}
