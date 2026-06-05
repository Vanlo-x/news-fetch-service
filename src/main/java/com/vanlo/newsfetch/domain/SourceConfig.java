package com.vanlo.newsfetch.domain;

import java.util.List;
import java.util.Map;

public record SourceConfig(
        String id,
        String name,
        SourceType type,
        boolean enabled,
        int priority,
        String category,
        String language,
        String region,
        String url,
        String method,
        Map<String, String> headers,
        Map<String, String> params,
        int timeoutMs,
        int retryCount,
        List<String> fallbackSourceIds,
        Map<String, Object> parserConfig,
        Integer rateLimit,
        Long cacheTtlSeconds
) {
}
