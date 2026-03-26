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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedditHarvester {

    private final WebClient webClient;
    private final HarvesterSupport support;
    private final CompetitorService competitorService;
    private final HarvestActivityService harvestActivity;

    @Scheduled(cron = "${aegis.sources.reddit.cron:0 */30 * * * *}")
    public void harvest() {
        List<String> competitors = competitorService.getNames();
        competitors.forEach(this::fetchForCompetitor);
        harvestActivity.record("REDDIT");
    }

    private void fetchForCompetitor(String competitor) {
        webClient.get()
                .uri("https://www.reddit.com/search.json?q={q}&sort=new&limit=15&t=day",
                        competitor)
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(RedditResponse.class)
                .doOnError(e -> log.warn("[Reddit] Error for {}: {}", competitor, e.getMessage()))
                .onErrorComplete()
                .subscribe(resp -> {
                    if (resp == null || resp.data() == null || resp.data().children() == null) return;
                    int count = 0;
                    for (RedditResponse.Child child : resp.data().children()) {
                        RedditResponse.Post post = child.data();
                        if (post == null || post.title() == null) continue;
                        String url = "https://www.reddit.com" + post.permalink();
                        String content = post.selftext() != null && !post.selftext().isBlank()
                                ? post.selftext() : post.title();
                        OffsetDateTime published = post.createdUtc() != null
                                ? Instant.ofEpochSecond(post.createdUtc().longValue()).atOffset(ZoneOffset.UTC)
                                : OffsetDateTime.now(ZoneOffset.UTC);
                        if (support.saveAndDispatch(competitor, post.title(), content, url, published, "REDDIT")) {
                            count++;
                        }
                    }
                    if (count > 0) log.info("[Reddit] {} new posts for {}", count, competitor);
                });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RedditResponse(Data data) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Data(List<Child> children) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Child(Post data) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Post(
                String title,
                String url,
                String selftext,
                String permalink,
                @JsonProperty("created_utc") Double createdUtc
        ) {}
    }
}
