package com.vanlo.newsfetch.domain;

import java.time.Instant;

public record SourceStatus(
        String sourceId,
        String sourceName,
        SourceType sourceType,
        boolean enabled,
        SourceHealth health,
        Instant lastFetchAt,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        String lastErrorCode,
        String lastErrorMessage,
        int lastItemCount,
        long lastDurationMs,
        boolean lastCacheHit,
        boolean lastStaleCacheHit,
        Long lastCacheAgeSeconds,
        Instant lastCacheRefreshedAt,
        boolean lastFallbackUsed,
        String lastResolvedSourceId
) {
}
