package com.vanlo.newsfetch.config;

import com.vanlo.newsfetch.domain.SourceConfig;
import com.vanlo.newsfetch.domain.SourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "news-fetch")
public record NewsFetchProperties(
        List<@Valid Source> sources
) {

    public NewsFetchProperties {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public record Source(
            @NotBlank
            @Size(max = 100)
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "must contain only letters, numbers, underscores, or hyphens")
            String id,

            @NotBlank
            @Size(max = 200)
            String name,

            @NotNull
            SourceType type,

            Boolean enabled,

            @Min(0)
            @Max(1000)
            Integer priority,

            @Size(max = 64)
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "must contain only letters, numbers, underscores, or hyphens")
            String category,

            @Pattern(regexp = "^[a-z]{2}$", message = "must be a lowercase ISO 639 language code")
            String language,

            @Pattern(regexp = "^[A-Z]{2}$", message = "must be an uppercase ISO 3166 region code")
            String region,

            @NotBlank
            String url,

            @Pattern(regexp = "^(GET|POST)$", message = "must be GET or POST")
            String method,

            Map<String, String> headers,
            Map<String, String> params,

            @Min(100)
            @Max(30000)
            Integer timeoutMs,

            @Min(1024)
            @Max(5242880)
            Integer maxResponseBytes,

            @Min(0)
            @Max(5)
            Integer retryCount,

            List<@NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "must contain only letters, numbers, underscores, or hyphens") String> fallbackSourceIds,
            Map<String, Object> parserConfig,

            @Positive
            Integer rateLimit,

            @PositiveOrZero
            Long cacheTtlSeconds
    ) {

        SourceConfig toDomain() {
            return new SourceConfig(
                    id,
                    name,
                    type,
                    enabled == null || enabled,
                    priority == null ? 100 : priority,
                    category,
                    language,
                    region,
                    url,
                    method == null ? "GET" : method,
                    headers == null ? Map.of() : Map.copyOf(headers),
                    params == null ? Map.of() : Map.copyOf(params),
                    timeoutMs == null ? 5000 : timeoutMs,
                    maxResponseBytes == null ? 1048576 : maxResponseBytes,
                    retryCount == null ? 0 : retryCount,
                    fallbackSourceIds == null ? List.of() : List.copyOf(fallbackSourceIds),
                    parserConfig == null ? Map.of() : Map.copyOf(parserConfig),
                    rateLimit,
                    cacheTtlSeconds
            );
        }
    }
}
