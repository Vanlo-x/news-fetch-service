package com.vanlo.newsfetch.adapters;

import com.vanlo.newsfetch.domain.FetchError;
import com.vanlo.newsfetch.domain.NewsItem;

import java.util.List;

public record RssFetchResult(
        List<NewsItem> items,
        List<FetchError> errors
) {

    public RssFetchResult {
        items = items == null ? List.of() : List.copyOf(items);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
