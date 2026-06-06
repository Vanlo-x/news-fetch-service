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
    public Optional<SourceFetchResult> get(String sourceId) {
        CachedSourceFetchResult entry = entries.get(sourceId);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(clock.instant())) {
            entries.remove(sourceId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.result());
    }

    @Override
    public void put(String sourceId, SourceFetchResult result, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        entries.put(sourceId, new CachedSourceFetchResult(result, clock.instant().plus(ttl)));
    }

    private record CachedSourceFetchResult(
            SourceFetchResult result,
            Instant expiresAt
    ) {
    }
}
