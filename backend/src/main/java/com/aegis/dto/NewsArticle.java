package com.aegis.dto;

import java.time.OffsetDateTime;

/** Internal DTO for raw NewsAPI article before enrichment. */
public record NewsArticle(
        String competitor,
        String title,
        String description,
        String url,
        OffsetDateTime publishedAt
) {}
