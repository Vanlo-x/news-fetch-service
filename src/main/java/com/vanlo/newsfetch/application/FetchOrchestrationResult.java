package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.domain.FetchError;
import com.vanlo.newsfetch.domain.FetchStatus;
import com.vanlo.newsfetch.domain.NewsItem;

import java.util.List;

public record FetchOrchestrationResult(
        FetchStatus status,
        List<NewsItem> items,
        List<FetchError> errors
) {

    public FetchOrchestrationResult {
        items = items == null ? List.of() : List.copyOf(items);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
