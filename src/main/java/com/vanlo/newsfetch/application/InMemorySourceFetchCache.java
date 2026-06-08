package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.adapters.SourceFetchResult;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemorySourceFetchCache implements SourceFetchCache {

    private final Clock clock;
    private final ConcurrentMap<String, CachedSourceFetchResult> entries = new ConcurrentHashMap<>();

    public InMemorySourceFetchCache() {
        this(Clock.systemUTC());
    }

    InMemorySourceFetchCache(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<CachedSourceFetchResult> get(String sourceId) {
        CachedSourceFetchResult entry = entries.get(sourceId);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiredAt(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    @Override
    public Optional<CachedSourceFetchResult> getStale(String sourceId) {
        return Optional.ofNullable(entries.get(sourceId));
    }

    @Override
    public void put(String sourceId, SourceFetchResult result, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        Instant now = clock.instant();
        entries.put(sourceId, new CachedSourceFetchResult(result, now, now.plus(ttl)));
    }
}
