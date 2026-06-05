package com.vanlo.newsfetch.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record NewsItem(
        String id,
        String title,
        String url,
        String sourceId,
        String sourceName,
        Instant publishedAt,
        Instant fetchedAt,
        String summary,
        String content,
        String author,
        String imageUrl,
        String category,
        String language,
        String region,
        List<String> tags,
        String fingerprint,
        Double qualityScore,
        Map<String, Object> raw
) {
}
