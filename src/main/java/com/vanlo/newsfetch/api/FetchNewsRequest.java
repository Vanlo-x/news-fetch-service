package com.vanlo.newsfetch.api;

import java.util.List;

public record FetchNewsRequest(
        List<String> sourceIds,
        String category,
        String language,
        String region,
        Integer limit
) {
}
