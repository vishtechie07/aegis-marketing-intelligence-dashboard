package com.aegis.harvester;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.aegis.service.CompetitorService;
import com.aegis.service.HarvestActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("deprecation")
public class RssHarvester {

    private final HarvesterSupport support;
    private final CompetitorService competitorService;
    private final HarvestActivityService harvestActivity;

    @Value("${aegis.sources.rss.feeds}")
    private String feedsRaw;

    @Scheduled(cron = "${aegis.sources.rss.cron:0 */30 * * * *}")
    public void harvest() {
        List<String> competitors = competitorService.getNames();
        String raw = feedsRaw != null ? feedsRaw : "";
        List<String> feeds = Arrays.stream(raw.split(",")).map(String::trim).toList();
        int saved = 0;
        for (String feedUrl : feeds) {
            saved += fetchFeed(feedUrl, competitors);
        }
        log.info("[RSS] Cycle complete — {} new articles from {} feeds", saved, feeds.size());
        harvestActivity.record("RSS");
    }

    private int fetchFeed(String feedUrl, List<String> competitors) {
        try {
            SyndFeedInput input = new SyndFeedInput();
            URL url = new URL(feedUrl);
            SyndFeed syndFeed = input.build(new XmlReader(url));
            int count = 0;
            for (SyndEntry entry : syndFeed.getEntries()) {
                String title = entry.getTitle();
                String link = entry.getLink();
                if (title == null || link == null) continue;

                String text = (title + " " + descriptionOf(entry)).toLowerCase();
                for (String competitor : competitors) {
                    if (text.contains(competitor.toLowerCase())) {
                        String content = descriptionOf(entry);
                        OffsetDateTime published = entry.getPublishedDate() != null
                                ? entry.getPublishedDate().toInstant().atOffset(ZoneOffset.UTC)
                                : OffsetDateTime.now(ZoneOffset.UTC);
                        if (support.saveAndDispatch(competitor, title, content, link, published, "RSS")) {
                            count++;
                        }
                        break;
                    }
                }
            }
            return count;
        } catch (Exception e) {
            log.warn("[RSS] Failed to parse feed {}: {}", feedUrl, e.getMessage());
            return 0;
        }
    }

    private String descriptionOf(SyndEntry entry) {
        if (entry.getDescription() != null) return entry.getDescription().getValue();
        if (!entry.getContents().isEmpty()) return entry.getContents().get(0).getValue();
        return "";
    }
}
