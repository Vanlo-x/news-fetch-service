package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.adapters.SourceFetchResult;

import java.time.Duration;
import java.time.Instant;

public record CachedSourceFetchResult(
        SourceFetchResult result,
        Instant cachedAt,
        Instant expiresAt
) {

    public Duration ageAt(Instant now) {
        if (now == null || cachedAt == null) {
            return Duration.ZERO;
        }
        Duration age = Duration.between(cachedAt, now);
        return age.isNegative() ? Duration.ZERO : age;
    }

    public boolean expiredAt(Instant now) {
        return expiresAt != null && now != null && !expiresAt.isAfter(now);
    }
}
