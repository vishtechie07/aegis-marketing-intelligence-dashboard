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
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GitHubHarvester {

    private final WebClient webClient;
    private final HarvesterSupport support;
    private final CompetitorService competitorService;
    private final HarvestActivityService harvestActivity;

    @Scheduled(cron = "${aegis.sources.github.cron:0 0 * * * *}")
    public void harvest() {
        List<String> competitors = competitorService.getNames();
        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minus(2, ChronoUnit.HOURS);
        competitors.forEach(c -> fetchForCompetitor(c, since));
        harvestActivity.record("GITHUB");
    }

    private void fetchForCompetitor(String competitor, OffsetDateTime since) {
        String org = competitorService.getGithubOrg(competitor);
        webClient.get()
                .uri("https://api.github.com/orgs/{org}/repos?sort=pushed&direction=desc&per_page=10&type=public", org)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToFlux(GhRepo.class)
                .filter(r -> r.pushedAt() != null && OffsetDateTime.parse(r.pushedAt()).isAfter(since))
                .doOnError(e -> log.warn("[GitHub] Error for org {}: {}", org, e.getMessage()))
                .onErrorComplete()
                .subscribe(repo -> {
                    String title = String.format("[GitHub] %s/%s — %s",
                            org, repo.name(),
                            repo.description() != null ? repo.description() : "updated");
                    String content = String.format("Repo: %s | Stars: %d | Topics: %s",
                            repo.fullName(), repo.stargazersCount(),
                            repo.topics() != null ? String.join(", ", repo.topics()) : "none");
                    OffsetDateTime pushed = OffsetDateTime.parse(repo.pushedAt());
                    if (support.saveAndDispatch(competitor, title, content, repo.htmlUrl(), pushed, "GITHUB")) {
                        log.info("[GitHub] New activity: {}/{}", org, repo.name());
                    }
                });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GhRepo(
            String name,
            @JsonProperty("full_name") String fullName,
            String description,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("pushed_at") String pushedAt,
            @JsonProperty("stargazers_count") int stargazersCount,
            List<String> topics
    ) {}
}
