package com.vanlo.newsfetch.config;

import com.vanlo.newsfetch.infrastructure.SourceUrlValidationResult;
import com.vanlo.newsfetch.infrastructure.SourceUrlValidator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SourceConfigValidator {

    private final SourceUrlValidator sourceUrlValidator;

    public SourceConfigValidator(SourceUrlValidator sourceUrlValidator) {
        this.sourceUrlValidator = sourceUrlValidator;
    }

    public void validate(List<NewsFetchProperties.Source> sources) {
        validateUniqueIds(sources);
        validateSourceUrls(sources);
    }

    private static void validateUniqueIds(List<NewsFetchProperties.Source> sources) {
        Map<String, Long> idCounts = sources.stream()
                .map(NewsFetchProperties.Source::id)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<String> duplicateIds = idCounts.entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        if (!duplicateIds.isEmpty()) {
            throw new IllegalArgumentException("Duplicate source ids: " + String.join(", ", duplicateIds));
        }
    }

    private void validateSourceUrls(List<NewsFetchProperties.Source> sources) {
        for (NewsFetchProperties.Source source : sources) {
            SourceUrlValidationResult result = sourceUrlValidator.validate(source.url());
            if (!result.allowed()) {
                throw new IllegalArgumentException(
                        "Unsafe source URL for source '%s': %s".formatted(source.id(), result.reason()));
            }
        }
    }
}
