package com.aegis.harvester;

import com.aegis.service.CompetitorService;
import com.aegis.service.HarvestActivityService;
import com.aegis.dto.CompetitorDto;
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

/** Yahoo Finance quote summary per ticker; no API key. */
@Component
@RequiredArgsConstructor
@Slf4j
public class FinancialHarvester {

    private final WebClient webClient;
    private final HarvesterSupport support;
    private final CompetitorService competitorService;
    private final HarvestActivityService harvestActivity;

    private static final String YF_SUMMARY =
            "https://query1.finance.yahoo.com/v10/finance/quoteSummary/{ticker}" +
            "?modules=price,financialData,defaultKeyStatistics,earnings";

    @Scheduled(cron = "${aegis.sources.financial.cron:0 0 * * * *}")
    public void harvest() {
        List<CompetitorDto> tracked = competitorService.getWithTicker();
        if (tracked.isEmpty()) {
            log.debug("[Finance] No competitors with tickers configured, skipping.");
            harvestActivity.record("FINANCE");
            return;
        }
        tracked.forEach(c -> fetchForCompetitor(c.name(), c.ticker()));
        harvestActivity.record("FINANCE");
    }

    private void fetchForCompetitor(String competitor, String ticker) {
        webClient.get()
                .uri(YF_SUMMARY, ticker)
                .header("User-Agent", "Mozilla/5.0")
                .retrieve()
                .bodyToMono(YfResponse.class)
                .doOnError(e -> log.debug("[Finance] Error for {}: {}", ticker, e.getMessage()))
                .onErrorComplete()
                .subscribe(resp -> {
                    if (resp == null || resp.quoteSummary() == null) return;
                    YfResponse.Result result = resp.quoteSummary().first();
                    if (result == null || result.price() == null) return;

                    String title = buildTitle(competitor, ticker, result);
                    String content = buildContent(result);
                    String url = "https://finance.yahoo.com/quote/" + ticker;

                    if (support.saveAndDispatch(competitor, title, content, url,
                            OffsetDateTime.now(ZoneOffset.UTC), "FINANCE")) {
                        log.info("[Finance] Financial snapshot saved for {} ({})", competitor, ticker);
                    }
                });
    }

    private String buildTitle(String competitor, String ticker, YfResponse.Result r) {
        YfResponse.PriceData p = r.price();
        String change = p.regularMarketChangePercent() != null
                ? String.format("%+.2f%%", p.regularMarketChangePercent() * 100)
                : "N/A";
        return String.format("[%s] %s stock %s — Market cap: %s",
                ticker, competitor, change, formatLarge(p.marketCap()));
    }

    private String buildContent(YfResponse.Result r) {
        StringBuilder sb = new StringBuilder();
        if (r.price() != null) {
            YfResponse.PriceData p = r.price();
            sb.append(String.format("Price: $%.2f | 52w High: %s | 52w Low: %s",
                    p.regularMarketPrice() != null ? p.regularMarketPrice() : 0,
                    formatNum(p.fiftyTwoWeekHigh()),
                    formatNum(p.fiftyTwoWeekLow())));
        }
        if (r.financialData() != null) {
            YfResponse.FinancialData f = r.financialData();
            if (f.revenueGrowth() != null)
                sb.append(String.format(" | Revenue growth: %+.1f%%", f.revenueGrowth() * 100));
            if (f.grossMargins() != null)
                sb.append(String.format(" | Gross margin: %.1f%%", f.grossMargins() * 100));
            if (f.totalDebt() != null)
                sb.append(String.format(" | Total debt: %s", formatLarge(f.totalDebt())));
        }
        if (r.defaultKeyStatistics() != null && r.defaultKeyStatistics().trailingEps() != null) {
            sb.append(String.format(" | EPS: $%.2f", r.defaultKeyStatistics().trailingEps()));
        }
        return sb.toString();
    }

    private String formatLarge(Double v) {
        if (v == null) return "N/A";
        if (v >= 1_000_000_000_000D) return String.format("$%.2fT", v / 1_000_000_000_000D);
        if (v >= 1_000_000_000D) return String.format("$%.2fB", v / 1_000_000_000D);
        if (v >= 1_000_000D) return String.format("$%.2fM", v / 1_000_000D);
        return String.format("$%.0f", v);
    }

    private String formatNum(Double v) {
        return v != null ? String.format("%.2f", v) : "N/A";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record YfResponse(@JsonProperty("quoteSummary") QuoteSummary quoteSummary) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record QuoteSummary(List<Result> result) {
            public Result first() { return result != null && !result.isEmpty() ? result.get(0) : null; }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Result(
                PriceData price,
                FinancialData financialData,
                KeyStats defaultKeyStatistics,
                Earnings earnings) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record PriceData(
                @JsonProperty("regularMarketPrice") RawDouble regularMarketPriceRaw,
                @JsonProperty("regularMarketChangePercent") RawDouble regularMarketChangePercentRaw,
                @JsonProperty("marketCap") RawDouble marketCapRaw,
                @JsonProperty("fiftyTwoWeekHigh") RawDouble fiftyTwoWeekHighRaw,
                @JsonProperty("fiftyTwoWeekLow") RawDouble fiftyTwoWeekLowRaw) {

            Double regularMarketPrice() { return raw(regularMarketPriceRaw); }
            Double regularMarketChangePercent() { return raw(regularMarketChangePercentRaw); }
            Double marketCap() { return raw(marketCapRaw); }
            Double fiftyTwoWeekHigh() { return raw(fiftyTwoWeekHighRaw); }
            Double fiftyTwoWeekLow() { return raw(fiftyTwoWeekLowRaw); }
            private static Double raw(RawDouble r) { return r != null ? r.raw() : null; }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record FinancialData(
                @JsonProperty("revenueGrowth") RawDouble revenueGrowthRaw,
                @JsonProperty("grossMargins") RawDouble grossMarginsRaw,
                @JsonProperty("totalDebt") RawDouble totalDebtRaw) {

            Double revenueGrowth() { return raw(revenueGrowthRaw); }
            Double grossMargins() { return raw(grossMarginsRaw); }
            Double totalDebt() { return raw(totalDebtRaw); }
            private static Double raw(RawDouble r) { return r != null ? r.raw() : null; }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record KeyStats(@JsonProperty("trailingEps") RawDouble trailingEpsRaw) {
            Double trailingEps() { return trailingEpsRaw != null ? trailingEpsRaw.raw() : null; }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Earnings(EarningsChart earningsChart) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record EarningsChart(List<QuarterlyEarnings> quarterly) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record QuarterlyEarnings(String date, RawDouble actual, RawDouble estimate) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record RawDouble(Double raw) {}
    }
}
