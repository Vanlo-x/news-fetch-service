package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.adapters.RssFetchResult;
import com.vanlo.newsfetch.adapters.RssSourceAdapter;
import com.vanlo.newsfetch.api.FetchNewsRequest;
import com.vanlo.newsfetch.api.FetchNewsResponse;
import com.vanlo.newsfetch.config.SourceConfigRegistry;
import com.vanlo.newsfetch.domain.FetchError;
import com.vanlo.newsfetch.domain.FetchStatus;
import com.vanlo.newsfetch.domain.NewsItem;
import com.vanlo.newsfetch.domain.SourceConfig;
import com.vanlo.newsfetch.domain.SourceType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
public class FetchNewsUseCase {

    private static final int DEFAULT_LIMIT = 20;

    private final SourceConfigRegistry sourceConfigRegistry;
    private final RssSourceAdapter rssSourceAdapter;

    public FetchNewsUseCase(SourceConfigRegistry sourceConfigRegistry, RssSourceAdapter rssSourceAdapter) {
        this.sourceConfigRegistry = sourceConfigRegistry;
        this.rssSourceAdapter = rssSourceAdapter;
    }

    public FetchNewsResponse fetch(FetchNewsRequest request) {
        FetchNewsRequest safeRequest = request == null ? new FetchNewsRequest(null, null, null, null, null) : request;
        List<FetchError> errors = new ArrayList<>();
        List<SourceConfig> selectedSources = selectSources(safeRequest, errors);
        List<NewsItem> items = new ArrayList<>();

        for (SourceConfig sourceConfig : selectedSources) {
            RssFetchResult result = rssSourceAdapter.fetch(sourceConfig);
            items.addAll(result.items());
            errors.addAll(result.errors());
        }

        int limit = safeRequest.limit() == null ? DEFAULT_LIMIT : safeRequest.limit();
        List<NewsItem> limitedItems = items.stream()
                .limit(limit)
                .toList();

        return new FetchNewsResponse(status(limitedItems, errors), limitedItems, errors);
    }

    private List<SourceConfig> selectSources(FetchNewsRequest request, List<FetchError> errors) {
        if (request.sourceIds() == null || request.sourceIds().isEmpty()) {
            return sourceConfigRegistry.enabledSources()
                    .stream()
                    .filter(source -> source.type() == SourceType.RSS)
                    .filter(source -> matchesFilters(source, request))
                    .toList();
        }

        List<SourceConfig> selectedSources = new ArrayList<>();
        for (String sourceId : new LinkedHashSet<>(request.sourceIds())) {
            Optional<SourceConfig> sourceConfig = sourceConfigRegistry.findById(sourceId);
            if (sourceConfig.isEmpty()) {
                errors.add(selectionError(sourceId, "SOURCE_NOT_FOUND", "Source is not configured"));
                continue;
            }

            SourceConfig source = sourceConfig.get();
            if (!source.enabled()) {
                errors.add(selectionError(sourceId, "SOURCE_DISABLED", "Source is disabled"));
                continue;
            }
            if (source.type() != SourceType.RSS) {
                errors.add(selectionError(sourceId, "UNSUPPORTED_SOURCE_TYPE", "Only RSS sources are supported"));
                continue;
            }
            if (!matchesFilters(source, request)) {
                continue;
            }

            selectedSources.add(source);
        }

        return selectedSources;
    }

    private static boolean matchesFilters(SourceConfig source, FetchNewsRequest request) {
        return matchesFilter(request.category(), source.category())
                && matchesFilter(request.language(), source.language())
                && matchesFilter(request.region(), source.region());
    }

    private static boolean matchesFilter(String requestedValue, String sourceValue) {
        return requestedValue == null || requestedValue.equals(sourceValue);
    }

    private static FetchError selectionError(String sourceId, String code, String message) {
        return new FetchError(sourceId, "SOURCE_SELECTION", code, message, false, Instant.now());
    }

    private static FetchStatus status(List<NewsItem> items, List<FetchError> errors) {
        if (!items.isEmpty() && errors.isEmpty()) {
            return FetchStatus.OK;
        }
        if (!items.isEmpty()) {
            return FetchStatus.PARTIAL;
        }
        if (!errors.isEmpty()) {
            return FetchStatus.FAILED;
        }
        return FetchStatus.OK;
    }
}
