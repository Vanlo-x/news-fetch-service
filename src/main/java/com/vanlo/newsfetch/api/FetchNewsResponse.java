package com.vanlo.newsfetch.api;

import com.vanlo.newsfetch.domain.FetchError;
import com.vanlo.newsfetch.domain.FetchStatus;
import com.vanlo.newsfetch.domain.NewsItem;

import java.util.List;

public record FetchNewsResponse(
        FetchStatus status,
        List<NewsItem> items,
        List<FetchError> errors
) {
}
