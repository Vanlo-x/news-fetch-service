package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.domain.FetchError;
import com.vanlo.newsfetch.domain.SourceConfig;
import com.vanlo.newsfetch.domain.SourceHealth;

import java.time.Instant;

public record SourceStatusUpdate(
        SourceConfig sourceConfig,
        SourceHealth health,
        Instant occurredAt,
        int itemCount,
        long durationMs,
        boolean cacheHit,
        boolean fallbackUsed,
        String resolvedSourceId,
        FetchError error
) {
}
