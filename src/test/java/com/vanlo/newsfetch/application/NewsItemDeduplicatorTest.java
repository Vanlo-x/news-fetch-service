package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.domain.NewsItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NewsItemDeduplicatorTest {

    private final NewsItemDeduplicator deduplicator = new NewsItemDeduplicator();

    @Test
    void keepsFirstItemForEachFingerprint() {
        NewsItem first = item("1", "same-fingerprint", "First");
        NewsItem duplicate = item("2", "same-fingerprint", "Duplicate");
        NewsItem second = item("3", "other-fingerprint", "Second");

        List<NewsItem> result = deduplicator.deduplicate(List.of(first, duplicate, second));

        assertThat(result).extracting(NewsItem::title).containsExactly("First", "Second");
    }

    private static NewsItem item(String id, String fingerprint, String title) {
        return new NewsItem(
                id,
                title,
                "https://example.com/" + id,
                "source-a",
                "Source A",
                null,
                Instant.parse("2026-06-06T01:00:00Z"),
                null,
                null,
                null,
                null,
                "world",
                "en",
                "GB",
                List.of(),
                fingerprint,
                null,
                Map.of()
        );
    }
}
