package com.vanlo.newsfetch.application;

import java.util.List;

public record FetchNewsCommand(
        List<String> sourceIds,
        String category,
        String language,
        String region,
        int limit
) {

    public FetchNewsCommand {
        sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
    }
}
