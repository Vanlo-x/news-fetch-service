package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.domain.NewsItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NewsItemNormalizerTest {

    private final NewsItemNormalizer normalizer = new NewsItemNormalizer();

    @Test
    void normalizesTextUrlAndFingerprint() {
        NewsItem item = item("old-id", "  A\u00a0 title   with   spaces  ", "HTTPS://Example.COM/a/../news/1#fragment");

        NewsItem normalized = normalizer.normalize(item);

        assertThat(normalized.title()).isEqualTo("A title with spaces");
        assertThat(normalized.url()).isEqualTo("https://example.com/news/1");
        assertThat(normalized.id()).isEqualTo(normalized.fingerprint());
        assertThat(normalized.fingerprint()).isNotEqualTo("old-id");
    }

    @Test
    void usesSameFingerprintForSameCanonicalUrlAcrossSources() {
        NewsItem first = normalizer.normalize(item("first", "Title A", "https://example.com/news/1#source-a"));
        NewsItem second = normalizer.normalize(item("second", "Title B", "https://EXAMPLE.com/news/1#source-b"));

        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
    }

    private static NewsItem item(String id, String title, String url) {
        return new NewsItem(
                id,
                title,
                url,
                "source-a",
                "Source A",
                Instant.parse("2026-06-06T00:00:00Z"),
                Instant.parse("2026-06-06T01:00:00Z"),
                " summary ",
                null,
                " author ",
                null,
                "world",
                "en",
                "GB",
                List.of(),
                id,
                null,
                Map.of()
        );
    }
}
