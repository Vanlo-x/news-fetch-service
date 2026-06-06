package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.adapters.SourceFetchResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySourceFetchCacheTest {

    @Test
    void returnsCachedResultBeforeTtlExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-06T00:00:00Z"));
        InMemorySourceFetchCache cache = new InMemorySourceFetchCache(clock);
        SourceFetchResult result = new SourceFetchResult(List.of(), List.of());

        cache.put("rss-a", result, Duration.ofSeconds(60));

        assertThat(cache.get("rss-a")).contains(result);
    }

    @Test
    void removesCachedResultAfterTtlExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-06T00:00:00Z"));
        InMemorySourceFetchCache cache = new InMemorySourceFetchCache(clock);
        SourceFetchResult result = new SourceFetchResult(List.of(), List.of());

        cache.put("rss-a", result, Duration.ofSeconds(60));
        clock.advance(Duration.ofSeconds(60));

        assertThat(cache.get("rss-a")).isEmpty();
    }

    @Test
    void ignoresNonPositiveTtl() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-06T00:00:00Z"));
        InMemorySourceFetchCache cache = new InMemorySourceFetchCache(clock);

        cache.put("rss-a", new SourceFetchResult(List.of(), List.of()), Duration.ZERO);

        assertThat(cache.get("rss-a")).isEmpty();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
