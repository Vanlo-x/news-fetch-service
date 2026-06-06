package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.adapters.NewsSourceAdapter;
import com.vanlo.newsfetch.adapters.SourceFetchResult;
import com.vanlo.newsfetch.config.SourceConfigRegistry;
import com.vanlo.newsfetch.domain.FetchError;
import com.vanlo.newsfetch.domain.FetchStatus;
import com.vanlo.newsfetch.domain.NewsItem;
import com.vanlo.newsfetch.domain.SourceConfig;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
public class FetchOrchestrator {

    private final SourceConfigRegistry sourceConfigRegistry;
    private final List<NewsSourceAdapter> sourceAdapters;
    private final NewsItemNormalizer newsItemNormalizer;
    private final NewsItemDeduplicator newsItemDeduplicator;

    public FetchOrchestrator(
            SourceConfigRegistry sourceConfigRegistry,
            List<NewsSourceAdapter> sourceAdapters,
            NewsItemNormalizer newsItemNormalizer,
            NewsItemDeduplicator newsItemDeduplicator
    ) {
        this.sourceConfigRegistry = sourceConfigRegistry;
        this.sourceAdapters = List.copyOf(sourceAdapters);
        this.newsItemNormalizer = newsItemNormalizer;
        this.newsItemDeduplicator = newsItemDeduplicator;
    }

    public FetchOrchestrationResult fetch(FetchNewsCommand command) {
        List<FetchError> errors = new ArrayList<>();
        List<SourceConfig> selectedSources = selectSources(command, errors);
        List<NewsItem> fetchedItems = new ArrayList<>();

        for (SourceConfig sourceConfig : selectedSources) {
            Optional<NewsSourceAdapter> adapter = adapterFor(sourceConfig);
            if (adapter.isEmpty()) {
                errors.add(selectionError(sourceConfig.id(), "UNSUPPORTED_SOURCE_TYPE", "Source type is not supported"));
                continue;
            }

            SourceFetchResult result = adapter.get().fetch(sourceConfig);
            fetchedItems.addAll(result.items());
            errors.addAll(result.errors());
        }

        List<NewsItem> normalizedItems = fetchedItems.stream()
                .map(newsItemNormalizer::normalize)
                .toList();
        List<NewsItem> deduplicatedItems = newsItemDeduplicator.deduplicate(normalizedItems);
        List<NewsItem> limitedItems = deduplicatedItems.stream()
                .limit(command.limit())
                .toList();

        return new FetchOrchestrationResult(status(limitedItems, errors), limitedItems, errors);
    }

    private List<SourceConfig> selectSources(FetchNewsCommand command, List<FetchError> errors) {
        if (command.sourceIds().isEmpty()) {
            return sourceConfigRegistry.enabledSources()
                    .stream()
                    .filter(this::hasAdapter)
                    .filter(source -> matchesFilters(source, command))
                    .toList();
        }

        List<SourceConfig> selectedSources = new ArrayList<>();
        for (String sourceId : new LinkedHashSet<>(command.sourceIds())) {
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
            if (!hasAdapter(source)) {
                errors.add(selectionError(sourceId, "UNSUPPORTED_SOURCE_TYPE", "Source type is not supported"));
                continue;
            }
            if (!matchesFilters(source, command)) {
                continue;
            }

            selectedSources.add(source);
        }

        return selectedSources;
    }

    private boolean hasAdapter(SourceConfig sourceConfig) {
        return adapterFor(sourceConfig).isPresent();
    }

    private Optional<NewsSourceAdapter> adapterFor(SourceConfig sourceConfig) {
        return sourceAdapters.stream()
                .filter(adapter -> adapter.supports(sourceConfig.type()))
                .findFirst();
    }

    private static boolean matchesFilters(SourceConfig source, FetchNewsCommand command) {
        return matchesFilter(command.category(), source.category())
                && matchesFilter(command.language(), source.language())
                && matchesFilter(command.region(), source.region());
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
