package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.config.SourceConfigRegistry;
import com.vanlo.newsfetch.domain.FetchError;
import com.vanlo.newsfetch.domain.SourceConfig;
import com.vanlo.newsfetch.domain.SourceHealth;
import com.vanlo.newsfetch.domain.SourceStatus;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemorySourceStatusRegistry implements SourceStatusRegistry {

    private final SourceConfigRegistry sourceConfigRegistry;
    private final ConcurrentMap<String, SourceStatus> statuses = new ConcurrentHashMap<>();

    public InMemorySourceStatusRegistry(SourceConfigRegistry sourceConfigRegistry) {
        this.sourceConfigRegistry = sourceConfigRegistry;
    }

    @Override
    public void record(SourceStatusUpdate update) {
        SourceConfig sourceConfig = update.sourceConfig();
        FetchError error = update.error();
        SourceStatus previous = statuses.get(sourceConfig.id());
        statuses.put(sourceConfig.id(), new SourceStatus(
                sourceConfig.id(),
                sourceConfig.name(),
                sourceConfig.type(),
                sourceConfig.enabled(),
                update.health(),
                update.occurredAt(),
                successAt(update, previous),
                failureAt(update, previous),
                error == null ? null : error.code(),
                error == null ? null : error.message(),
                update.itemCount(),
                update.durationMs(),
                update.cacheHit(),
                update.fallbackUsed(),
                update.resolvedSourceId()
        ));
    }

    @Override
    public List<SourceStatus> allStatuses() {
        return sourceConfigRegistry.allSources()
                .stream()
                .map(source -> statuses.getOrDefault(source.id(), defaultStatus(source)))
                .sorted(Comparator.comparing(SourceStatus::sourceId))
                .toList();
    }

    @Override
    public Optional<SourceStatus> findBySourceId(String sourceId) {
        return sourceConfigRegistry.findById(sourceId)
                .map(source -> statuses.getOrDefault(source.id(), defaultStatus(source)));
    }

    private static SourceStatus defaultStatus(SourceConfig sourceConfig) {
        return new SourceStatus(
                sourceConfig.id(),
                sourceConfig.name(),
                sourceConfig.type(),
                sourceConfig.enabled(),
                sourceConfig.enabled() ? SourceHealth.UNKNOWN : SourceHealth.DISABLED,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                false,
                false,
                null
        );
    }

    private static java.time.Instant successAt(SourceStatusUpdate update, SourceStatus previous) {
        if (update.health() == SourceHealth.OK || update.health() == SourceHealth.DEGRADED) {
            return update.occurredAt();
        }
        return previous == null ? null : previous.lastSuccessAt();
    }

    private static java.time.Instant failureAt(SourceStatusUpdate update, SourceStatus previous) {
        if (update.health() == SourceHealth.FAILED || update.health() == SourceHealth.DEGRADED) {
            return update.occurredAt();
        }
        return previous == null ? null : previous.lastFailureAt();
    }
}
