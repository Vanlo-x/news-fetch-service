package com.vanlo.newsfetch.domain;

import java.time.Instant;

public record FetchError(
        String sourceId,
        String stage,
        String code,
        String message,
        boolean retryable,
        Instant occurredAt
) {
}
