package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.adapters.NewsSourceAdapter;
import com.vanlo.newsfetch.adapters.SourceFetchResult;
import com.vanlo.newsfetch.config.SourceConfigRegistry;
import com.vanlo.newsfetch.domain.FetchError;
import com.vanlo.newsfetch.domain.FetchStatus;
import com.vanlo.newsfetch.domain.NewsItem;
import com.vanlo.newsfetch.domain.SourceConfig;
import com.vanlo.newsfetch.domain.SourceHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FetchOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(FetchOrchestrator.class);

    private final SourceConfigRegistry sourceConfigRegistry;
    private final List<NewsSourceAdapter> sourceAdapters;
    private final NewsItemNormalizer newsItemNormalizer;
    private final NewsItemDeduplicator newsItemDeduplicator;
    private final NewsItemSorter newsItemSorter;
    private final SourceFetchCache sourceFetchCache;
    private final SourceStatusRegistry sourceStatusRegistry;

    public FetchOrchestrator(
            SourceConfigRegistry sourceConfigRegistry,
            List<NewsSourceAdapter> sourceAdapters,
            NewsItemNormalizer newsItemNormalizer,
            NewsItemDeduplicator newsItemDeduplicator,
            NewsItemSorter newsItemSorter,
            SourceFetchCache sourceFetchCache,
            SourceStatusRegistry sourceStatusRegistry
    ) {
        this.sourceConfigRegistry = sourceConfigRegistry;
        this.sourceAdapters = List.copyOf(sourceAdapters);
        this.newsItemNormalizer = newsItemNormalizer;
        this.newsItemDeduplicator = newsItemDeduplicator;
        this.newsItemSorter = newsItemSorter;
        this.sourceFetchCache = sourceFetchCache;
        this.sourceStatusRegistry = sourceStatusRegistry;
    }

    public FetchOrchestrationResult fetch(FetchNewsCommand command) {
        List<FetchError> errors = new ArrayList<>();
        List<SourceConfig> selectedSources = selectSources(command, errors);
        Map<String, Integer> sourceOrder = sourceOrder(selectedSources);
        List<NewsItem> fetchedItems = new ArrayList<>();

        for (SourceConfig sourceConfig : selectedSources) {
            Optional<NewsSourceAdapter> adapter = adapterFor(sourceConfig);
            if (adapter.isEmpty()) {
                errors.add(selectionError(sourceConfig.id(), "UNSUPPORTED_SOURCE_TYPE", "Source type is not supported"));
                continue;
            }

            SourceExecutionResult executionResult = executeSource(adapter.get(), sourceConfig);
            fetchedItems.addAll(executionResult.result().items());
            errors.addAll(executionResult.result().errors());
            appendMissingSourceOrders(sourceOrder, executionResult.result().items());
        }

        List<NewsItem> normalizedItems = fetchedItems.stream()
                .map(newsItemNormalizer::normalize)
                .toList();
        List<NewsItem> deduplicatedItems = newsItemDeduplicator.deduplicate(normalizedItems);
        List<NewsItem> sortedItems = newsItemSorter.sort(deduplicatedItems, sourceOrder);
        List<NewsItem> limitedItems = sortedItems.stream()
                .limit(command.limit())
                .toList();

        return new FetchOrchestrationResult(status(limitedItems, errors), limitedItems, errors);
    }

    private static Map<String, Integer> sourceOrder(List<SourceConfig> selectedSources) {
        Map<String, Integer> sourceOrder = new LinkedHashMap<>();
        for (SourceConfig sourceConfig : selectedSources) {
            sourceOrder.putIfAbsent(sourceConfig.id(), sourceOrder.size());
        }
        return sourceOrder;
    }

    private static void appendMissingSourceOrders(Map<String, Integer> sourceOrder, List<NewsItem> items) {
        for (NewsItem item : items) {
            if (item.sourceId() != null) {
                sourceOrder.putIfAbsent(item.sourceId(), sourceOrder.size());
            }
        }
    }

    private SourceExecutionResult executeSource(NewsSourceAdapter adapter, SourceConfig sourceConfig) {
        Instant startedAt = Instant.now();
        SourceExecutionResult executionResult = fetchWithCache(adapter, sourceConfig);
        SourceExecutionResult timedResult = executionResult.withDuration(Duration.between(startedAt, Instant.now()).toMillis());
        recordSourceStatus(sourceConfig, timedResult);
        return timedResult;
    }

    private SourceExecutionResult fetchWithCache(NewsSourceAdapter adapter, SourceConfig sourceConfig) {
        if (!cacheEnabled(sourceConfig)) {
            return fetchWithRetryAndFallback(adapter, sourceConfig);
        }

        Optional<SourceFetchResult> cachedResult = sourceFetchCache.get(sourceConfig.id());
        if (cachedResult.isPresent()) {
            return new SourceExecutionResult(cachedResult.get(), true, false, sourceConfig.id(), 0);
        }

        SourceExecutionResult result = fetchWithRetryAndFallback(adapter, sourceConfig);
        cacheSuccessfulResult(sourceConfig, result.result());
        return result;
    }

    private SourceExecutionResult fetchWithRetryAndFallback(NewsSourceAdapter adapter, SourceConfig sourceConfig) {
        SourceFetchResult primaryResult = fetchWithRetry(adapter, sourceConfig);
        if (!shouldFallback(sourceConfig, primaryResult)) {
            return new SourceExecutionResult(primaryResult, false, false, sourceConfig.id(), 0);
        }

        List<FetchError> errors = new ArrayList<>(primaryResult.errors());
        for (String fallbackSourceId : new LinkedHashSet<>(sourceConfig.fallbackSourceIds())) {
            Optional<SourceConfig> fallbackSource = fallbackSourceFor(sourceConfig, fallbackSourceId, errors);
            if (fallbackSource.isEmpty()) {
                continue;
            }

            NewsSourceAdapter fallbackAdapter = adapterFor(fallbackSource.get()).orElseThrow();
            SourceExecutionResult fallbackExecutionResult = executeFallbackSource(fallbackAdapter, fallbackSource.get());
            SourceFetchResult fallbackResult = fallbackExecutionResult.result();
            errors.addAll(fallbackResult.errors());
            if (!fallbackResult.items().isEmpty()) {
                return new SourceExecutionResult(
                        new SourceFetchResult(fallbackResult.items(), errors),
                        false,
                        true,
                        fallbackSource.get().id(),
                        0
                );
            }
        }

        return new SourceExecutionResult(new SourceFetchResult(List.of(), errors), false, false, sourceConfig.id(), 0);
    }

    private Optional<SourceConfig> fallbackSourceFor(SourceConfig primarySource, String fallbackSourceId, List<FetchError> errors) {
        if (primarySource.id().equals(fallbackSourceId)) {
            errors.add(fallbackSelectionError(fallbackSourceId, "FALLBACK_SOURCE_INVALID", "Fallback source must not reference itself"));
            return Optional.empty();
        }

        Optional<SourceConfig> sourceConfig = sourceConfigRegistry.findById(fallbackSourceId);
        if (sourceConfig.isEmpty()) {
            errors.add(fallbackSelectionError(fallbackSourceId, "FALLBACK_SOURCE_NOT_FOUND", "Fallback source is not configured"));
            return Optional.empty();
        }

        SourceConfig source = sourceConfig.get();
        if (!source.enabled()) {
            errors.add(fallbackSelectionError(fallbackSourceId, "FALLBACK_SOURCE_DISABLED", "Fallback source is disabled"));
            return Optional.empty();
        }
        if (!hasAdapter(source)) {
            errors.add(fallbackSelectionError(fallbackSourceId, "FALLBACK_SOURCE_UNSUPPORTED_TYPE", "Fallback source type is not supported"));
            return Optional.empty();
        }

        return Optional.of(source);
    }

    private static boolean shouldFallback(SourceConfig sourceConfig, SourceFetchResult result) {
        return !sourceConfig.fallbackSourceIds().isEmpty()
                && result.items().isEmpty()
                && !result.errors().isEmpty();
    }

    private SourceExecutionResult executeFallbackSource(NewsSourceAdapter adapter, SourceConfig sourceConfig) {
        Instant startedAt = Instant.now();
        SourceExecutionResult executionResult = fetchSingleSourceWithCache(adapter, sourceConfig);
        SourceExecutionResult timedResult = executionResult.withDuration(Duration.between(startedAt, Instant.now()).toMillis());
        recordSourceStatus(sourceConfig, timedResult);
        return timedResult;
    }

    private SourceExecutionResult fetchSingleSourceWithCache(NewsSourceAdapter adapter, SourceConfig sourceConfig) {
        if (!cacheEnabled(sourceConfig)) {
            return new SourceExecutionResult(fetchWithRetry(adapter, sourceConfig), false, false, sourceConfig.id(), 0);
        }

        Optional<SourceFetchResult> cachedResult = sourceFetchCache.get(sourceConfig.id());
        if (cachedResult.isPresent()) {
            return new SourceExecutionResult(cachedResult.get(), true, false, sourceConfig.id(), 0);
        }

        SourceFetchResult result = fetchWithRetry(adapter, sourceConfig);
        cacheSuccessfulResult(sourceConfig, result);
        return new SourceExecutionResult(result, false, false, sourceConfig.id(), 0);
    }

    private void cacheSuccessfulResult(SourceConfig sourceConfig, SourceFetchResult result) {
        if (cacheEnabled(sourceConfig) && !result.items().isEmpty() && result.errors().isEmpty()) {
            sourceFetchCache.put(sourceConfig.id(), result, Duration.ofSeconds(sourceConfig.cacheTtlSeconds()));
        }
    }

    private static boolean cacheEnabled(SourceConfig sourceConfig) {
        Long cacheTtlSeconds = sourceConfig.cacheTtlSeconds();
        return cacheTtlSeconds != null && cacheTtlSeconds > 0;
    }

    private void recordSourceStatus(SourceConfig sourceConfig, SourceExecutionResult executionResult) {
        SourceFetchResult result = executionResult.result();
        FetchError firstError = result.errors().isEmpty() ? null : result.errors().getFirst();
        SourceHealth health = sourceHealth(result, executionResult.fallbackUsed());
        sourceStatusRegistry.record(new SourceStatusUpdate(
                sourceConfig,
                health,
                Instant.now(),
                result.items().size(),
                executionResult.durationMs(),
                executionResult.cacheHit(),
                executionResult.fallbackUsed(),
                executionResult.resolvedSourceId(),
                firstError
        ));
        logSourceExecution(sourceConfig, executionResult, health, firstError);
    }

    private static SourceHealth sourceHealth(SourceFetchResult result, boolean fallbackUsed) {
        if (!result.items().isEmpty() && fallbackUsed) {
            return SourceHealth.DEGRADED;
        }
        if (!result.items().isEmpty() && result.errors().isEmpty()) {
            return SourceHealth.OK;
        }
        if (!result.items().isEmpty()) {
            return SourceHealth.DEGRADED;
        }
        if (!result.errors().isEmpty()) {
            return SourceHealth.FAILED;
        }
        return SourceHealth.OK;
    }

    private static void logSourceExecution(
            SourceConfig sourceConfig,
            SourceExecutionResult executionResult,
            SourceHealth health,
            FetchError firstError
    ) {
        if (health == SourceHealth.FAILED) {
            LOGGER.warn(
                    "source_fetch_completed sourceId={} health={} items={} errors={} durationMs={} cacheHit={} fallbackUsed={} resolvedSourceId={} errorCode={}",
                    sourceConfig.id(),
                    health,
                    executionResult.result().items().size(),
                    executionResult.result().errors().size(),
                    executionResult.durationMs(),
                    executionResult.cacheHit(),
                    executionResult.fallbackUsed(),
                    executionResult.resolvedSourceId(),
                    firstError == null ? null : firstError.code()
            );
            return;
        }

        LOGGER.info(
                "source_fetch_completed sourceId={} health={} items={} errors={} durationMs={} cacheHit={} fallbackUsed={} resolvedSourceId={}",
                sourceConfig.id(),
                health,
                executionResult.result().items().size(),
                executionResult.result().errors().size(),
                executionResult.durationMs(),
                executionResult.cacheHit(),
                executionResult.fallbackUsed(),
                executionResult.resolvedSourceId()
        );
    }

    private static SourceFetchResult fetchWithRetry(NewsSourceAdapter adapter, SourceConfig sourceConfig) {
        int maxAttempts = sourceConfig.retryCount() + 1;
        SourceFetchResult result = new SourceFetchResult(List.of(), List.of());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            result = adapter.fetch(sourceConfig);
            if (attempt == maxAttempts || !shouldRetry(result)) {
                return result;
            }
        }

        return result;
    }

    private static boolean shouldRetry(SourceFetchResult result) {
        return result.items().isEmpty()
                && !result.errors().isEmpty()
                && result.errors().stream().allMatch(FetchError::retryable);
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

    private static FetchError fallbackSelectionError(String sourceId, String code, String message) {
        return new FetchError(sourceId, "FALLBACK_SELECTION", code, message, false, Instant.now());
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

    private record SourceExecutionResult(
            SourceFetchResult result,
            boolean cacheHit,
            boolean fallbackUsed,
            String resolvedSourceId,
            long durationMs
    ) {

        private SourceExecutionResult withDuration(long durationMs) {
            return new SourceExecutionResult(result, cacheHit, fallbackUsed, resolvedSourceId, durationMs);
        }
    }
}
