package com.aegis.harvester;

import com.aegis.service.CompetitorService;
import com.aegis.service.HarvestActivityService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** USASpending.gov contract awards; no API key. */
@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class ContractHarvester {

    private final WebClient webClient;
    private final HarvesterSupport support;
    private final CompetitorService competitorService;
    private final HarvestActivityService harvestActivity;

    private static final String USASPENDING_URL =
            "https://api.usaspending.gov/api/v2/search/spending_by_award/";

    @Scheduled(cron = "${aegis.sources.contracts.cron:0 0 */4 * * *}")
    public void harvest() {
        String startDate = LocalDate.now(ZoneOffset.UTC).minusDays(7).toString();
        List<String> competitors = competitorService.getNames();
        competitors.forEach(c -> fetchForCompetitor(c, startDate));
        harvestActivity.record("CONTRACT");
    }

    private void fetchForCompetitor(String competitor, String startDate) {
        Map<String, Object> body = Map.of(
                "filters", Map.of(
                        "keywords", List.of(competitor),
                        "time_period", List.of(Map.of(
                                "start_date", startDate,
                                "end_date", LocalDate.now(ZoneOffset.UTC).toString())),
                        "award_type_codes", List.of("A", "B", "C", "D")
                ),
                "fields", List.of("Award ID", "Recipient Name", "Award Amount",
                        "Awarding Agency", "Award Type", "Start Date", "Description"),
                "page", 1,
                "limit", 10,
                "sort", "Award Amount",
                "order", "desc"
        );

        webClient.post()
                .uri(USASPENDING_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(ContractResponse.class)
                .doOnError(e -> log.debug("[Contracts] Error for {}: {}", competitor, e.getMessage()))
                .onErrorComplete()
                .subscribe(resp -> {
                    if (resp == null || resp.results() == null) return;
                    int count = 0;
                    for (ContractResponse.Award award : resp.results()) {
                        if (award.recipientName() == null) continue;
                        String title = buildTitle(competitor, award);
                        String content = buildContent(award);
                        String url = "https://www.usaspending.gov/award/" +
                                (award.awardId() != null ? award.awardId() : "");
                        OffsetDateTime date = parseDate(award.startDate());
                        if (support.saveAndDispatch(competitor, title, content, url, date, "CONTRACT")) {
                            count++;
                        }
                    }
                    if (count > 0) log.info("[Contracts] {} new awards involving {}", count, competitor);
                });
    }

    private String buildTitle(String competitor, ContractResponse.Award a) {
        return String.format("[US Gov Contract] %s awarded %s from %s",
                a.recipientName() != null ? a.recipientName() : competitor,
                a.awardAmount() != null ? formatAmount(a.awardAmount()) : "undisclosed",
                a.awardingAgency() != null ? a.awardingAgency() : "US Government");
    }

    private String buildContent(ContractResponse.Award a) {
        return String.format("Award Type: %s | Amount: %s | Agency: %s | Description: %s",
                a.awardType() != null ? a.awardType() : "Contract",
                a.awardAmount() != null ? formatAmount(a.awardAmount()) : "N/A",
                a.awardingAgency() != null ? a.awardingAgency() : "N/A",
                a.description() != null ? a.description() : "No description");
    }

    private String formatAmount(Double amt) {
        if (amt >= 1_000_000_000D) return String.format("$%.2fB", amt / 1_000_000_000D);
        if (amt >= 1_000_000D) return String.format("$%.1fM", amt / 1_000_000D);
        return String.format("$%.0f", amt);
    }

    private OffsetDateTime parseDate(String date) {
        try {
            return date != null
                    ? LocalDate.parse(date).atStartOfDay().atOffset(ZoneOffset.UTC)
                    : OffsetDateTime.now(ZoneOffset.UTC);
        } catch (Exception e) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContractResponse(List<Award> results) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Award(
                @JsonProperty("Award ID") String awardId,
                @JsonProperty("Recipient Name") String recipientName,
                @JsonProperty("Award Amount") Double awardAmount,
                @JsonProperty("Awarding Agency") String awardingAgency,
                @JsonProperty("Award Type") String awardType,
                @JsonProperty("Start Date") String startDate,
                @JsonProperty("Description") String description
        ) {}
    }
}
