package com.aegis.harvester;

import com.aegis.service.CompetitorService;
import com.aegis.service.HarvestActivityService;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Google News RSS per competitor; no API key. */
@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("deprecation")
public class GoogleNewsHarvester {

    private final HarvesterSupport support;
    private final CompetitorService competitorService;
    private final HarvestActivityService harvestActivity;

    private static final String GNEWS_URL =
            "https://news.google.com/rss/search?q=%s+news&hl=en-US&gl=US&ceid=US:en";

    @Scheduled(cron = "${aegis.sources.googlenews.cron:0 */5 * * * *}")
    public void harvest() {
        List<String> competitors = competitorService.getNames();
        int total = 0;
        for (String competitor : competitors) {
            total += fetchForCompetitor(competitor);
        }
        if (total > 0) log.info("[GoogleNews] Cycle complete — {} new articles", total);
        harvestActivity.record("GOOGLENEWS");
    }

    private int fetchForCompetitor(String competitor) {
        try {
            String encoded = java.net.URLEncoder.encode(competitor, java.nio.charset.StandardCharsets.UTF_8);
            URL url = new URL(String.format(GNEWS_URL, encoded));

            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(url));
            int count = 0;

            for (SyndEntry entry : feed.getEntries()) {
                String title = entry.getTitle();
                String link = entry.getLink();
                if (title == null || link == null) continue;

                String content = entry.getDescription() != null ? entry.getDescription().getValue() : "";
                OffsetDateTime published = entry.getPublishedDate() != null
                        ? entry.getPublishedDate().toInstant().atOffset(ZoneOffset.UTC)
                        : OffsetDateTime.now(ZoneOffset.UTC);

                if (support.saveAndDispatch(competitor, title, content, link, published, "GOOGLENEWS")) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            log.debug("[GoogleNews] Failed for {}: {}", competitor, e.getMessage());
            return 0;
        }
    }
}
