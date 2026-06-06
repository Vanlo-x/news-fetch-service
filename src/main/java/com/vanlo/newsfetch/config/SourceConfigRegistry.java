package com.vanlo.newsfetch.config;

import com.vanlo.newsfetch.domain.SourceConfig;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class SourceConfigRegistry {

    private final List<SourceConfig> sources;

    public SourceConfigRegistry(NewsFetchProperties properties, SourceConfigValidator sourceConfigValidator) {
        sourceConfigValidator.validate(properties.sources());
        this.sources = properties.sources()
                .stream()
                .map(NewsFetchProperties.Source::toDomain)
                .sorted(Comparator.comparingInt(SourceConfig::priority)
                        .thenComparing(SourceConfig::id))
                .toList();
    }

    public List<SourceConfig> allSources() {
        return sources;
    }

    public List<SourceConfig> enabledSources() {
        return sources.stream()
                .filter(SourceConfig::enabled)
                .toList();
    }

    public Optional<SourceConfig> findById(String id) {
        return sources.stream()
                .filter(source -> source.id().equals(id))
                .findFirst();
    }
}
