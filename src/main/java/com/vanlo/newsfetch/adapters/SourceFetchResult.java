package com.vanlo.newsfetch.adapters;

import com.vanlo.newsfetch.domain.FetchError;
import com.vanlo.newsfetch.domain.NewsItem;

import java.util.List;

public record SourceFetchResult(
        List<NewsItem> items,
        List<FetchError> errors
) {

    public SourceFetchResult {
        items = items == null ? List.of() : List.copyOf(items);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
