package com.vanlo.newsfetch.infrastructure;

import java.time.Duration;
import java.util.Map;

public record SourceHttpRequest(
        String url,
        String method,
        Map<String, String> headers,
        Duration timeout,
        int maxResponseBytes
) {

    public SourceHttpRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
