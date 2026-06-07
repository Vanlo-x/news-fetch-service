package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.domain.NewsItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NewsItemSorterTest {

    private final NewsItemSorter sorter = new NewsItemSorter();

    @Test
    void sortsNewestPublishedItemsFirst() {
        List<NewsItem> sorted = sorter.sort(List.of(
                item("old", "rss-a", Instant.parse("2026-06-06T01:00:00Z")),
                item("new", "rss-a", Instant.parse("2026-06-06T02:00:00Z"))
        ), Map.of("rss-a", 0));

        assertThat(sorted).extracting(NewsItem::id).containsExactly("new", "old");
    }

    @Test
    void putsItemsWithoutPublishedAtAfterPublishedItems() {
        List<NewsItem> sorted = sorter.sort(List.of(
                item("missing", "rss-a", null),
                item("published", "rss-a", Instant.parse("2026-06-06T01:00:00Z"))
        ), Map.of("rss-a", 0));

        assertThat(sorted).extracting(NewsItem::id).containsExactly("published", "missing");
    }

    @Test
    void usesSourceOrderWhenPublishedAtMatches() {
        Instant publishedAt = Instant.parse("2026-06-06T01:00:00Z");

        List<NewsItem> sorted = sorter.sort(List.of(
                item("b", "rss-b", publishedAt),
                item("a", "rss-a", publishedAt)
        ), Map.of("rss-a", 0, "rss-b", 1));

        assertThat(sorted).extracting(NewsItem::id).containsExactly("a", "b");
    }

    @Test
    void putsUnknownSourceOrderAfterKnownSources() {
        Instant publishedAt = Instant.parse("2026-06-06T01:00:00Z");

        List<NewsItem> sorted = sorter.sort(List.of(
                item("unknown", "rss-missing", publishedAt),
                item("known", "rss-a", publishedAt)
        ), Map.of("rss-a", 0));

        assertThat(sorted).extracting(NewsItem::id).containsExactly("known", "unknown");
    }

    @Test
    void keepsOriginalOrderWhenSortKeysMatch() {
        Instant publishedAt = Instant.parse("2026-06-06T01:00:00Z");

        List<NewsItem> sorted = sorter.sort(List.of(
                item("first", "rss-a", publishedAt),
                item("second", "rss-a", publishedAt)
        ), Map.of("rss-a", 0));

        assertThat(sorted).extracting(NewsItem::id).containsExactly("first", "second");
    }

    private static NewsItem item(String id, String sourceId, Instant publishedAt) {
        return new NewsItem(
                id,
                id,
                "https://example.com/" + id,
                sourceId,
                sourceId,
                publishedAt,
                Instant.parse("2026-06-06T03:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                id,
                null,
                Map.of()
        );
    }
}
