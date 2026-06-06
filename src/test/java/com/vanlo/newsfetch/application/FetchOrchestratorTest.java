package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.adapters.RssSourceAdapter;
import com.vanlo.newsfetch.config.NewsFetchProperties;
import com.vanlo.newsfetch.config.SourceConfigRegistry;
import com.vanlo.newsfetch.config.SourceConfigValidator;
import com.vanlo.newsfetch.domain.FetchStatus;
import com.vanlo.newsfetch.domain.SourceType;
import com.vanlo.newsfetch.infrastructure.SourceHttpClient;
import com.vanlo.newsfetch.infrastructure.SourceHttpResponse;
import com.vanlo.newsfetch.infrastructure.SourceUrlValidator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FetchOrchestratorTest {

    @Test
    void fetchesEnabledRssSourcesAndAppliesLimit() {
        FetchOrchestrator orchestrator = orchestratorWithSources(
                List.of(source("rss-a", SourceType.RSS, true), source("rss-b", SourceType.RSS, true)),
                successClient(twoItemRssFixture())
        );

        FetchOrchestrationResult result = orchestrator.fetch(new FetchNewsCommand(null, null, null, null, 1));

        assertThat(result.status()).isEqualTo(FetchStatus.OK);
        assertThat(result.items()).hasSize(1);
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void returnsPartialWhenSomeSourcesFail() {
        SourceHttpClient client = request -> {
            if (request.url().contains("rss-a")) {
                return new SourceHttpResponse(200, Map.of(), singleItemRssFixture("A").getBytes(StandardCharsets.UTF_8));
            }
            return new SourceHttpResponse(500, Map.of(), new byte[0]);
        };
        FetchOrchestrator orchestrator = orchestratorWithSources(
                List.of(source("rss-a", SourceType.RSS, true), source("rss-b", SourceType.RSS, true)),
                client
        );

        FetchOrchestrationResult result = orchestrator.fetch(new FetchNewsCommand(null, null, null, null, 20));

        assertThat(result.status()).isEqualTo(FetchStatus.PARTIAL);
        assertThat(result.items()).hasSize(1);
        assertThat(result.errors()).hasSize(1);
    }

    @Test
    void deduplicatesItemsAcrossSourcesBeforeApplyingLimit() {
        SourceHttpClient client = request -> new SourceHttpResponse(
                200,
                Map.of(),
                singleItemWithUrl("Shared title", "https://example.com/shared#tracking").getBytes(StandardCharsets.UTF_8)
        );
        FetchOrchestrator orchestrator = orchestratorWithSources(
                List.of(source("rss-a", SourceType.RSS, true), source("rss-b", SourceType.RSS, true)),
                client
        );

        FetchOrchestrationResult result = orchestrator.fetch(new FetchNewsCommand(null, null, null, null, 20));

        assertThat(result.status()).isEqualTo(FetchStatus.OK);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().url()).isEqualTo("https://example.com/shared");
    }

    @Test
    void returnsFailedForUnknownSourceId() {
        FetchOrchestrator orchestrator = orchestratorWithSources(
                List.of(source("rss-a", SourceType.RSS, true)),
                successClient(twoItemRssFixture())
        );

        FetchOrchestrationResult result = orchestrator.fetch(new FetchNewsCommand(List.of("missing"), null, null, null, 20));

        assertThat(result.status()).isEqualTo(FetchStatus.FAILED);
        assertThat(result.items()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().getFirst().code()).isEqualTo("SOURCE_NOT_FOUND");
    }

    @Test
    void ignoresUnsupportedSourcesWhenNoSourceIdsAreSpecified() {
        FetchOrchestrator orchestrator = orchestratorWithSources(
                List.of(source("api-a", SourceType.API, true)),
                successClient(twoItemRssFixture())
        );

        FetchOrchestrationResult result = orchestrator.fetch(new FetchNewsCommand(null, null, null, null, 20));

        assertThat(result.status()).isEqualTo(FetchStatus.OK);
        assertThat(result.items()).isEmpty();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void returnsErrorForExplicitUnsupportedSourceType() {
        FetchOrchestrator orchestrator = orchestratorWithSources(
                List.of(source("api-a", SourceType.API, true)),
                successClient(twoItemRssFixture())
        );

        FetchOrchestrationResult result = orchestrator.fetch(new FetchNewsCommand(List.of("api-a"), null, null, null, 20));

        assertThat(result.status()).isEqualTo(FetchStatus.FAILED);
        assertThat(result.errors().getFirst().code()).isEqualTo("UNSUPPORTED_SOURCE_TYPE");
    }

    @Test
    void retriesRetryableSourceFailuresAndReturnsSuccessfulAttempt() {
        AtomicInteger attempts = new AtomicInteger();
        SourceHttpClient client = request -> {
            if (attempts.incrementAndGet() == 1) {
                return new SourceHttpResponse(503, Map.of(), new byte[0]);
            }
            return new SourceHttpResponse(200, Map.of(), singleItemRssFixture("Recovered").getBytes(StandardCharsets.UTF_8));
        };
        FetchOrchestrator orchestrator = orchestratorWithSources(
                List.of(source("rss-a", SourceType.RSS, true, 1)),
                client
        );

        FetchOrchestrationResult result = orchestrator.fetch(new FetchNewsCommand(List.of("rss-a"), null, null, null, 20));

        assertThat(attempts).hasValue(2);
        assertThat(result.status()).isEqualTo(FetchStatus.OK);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().title()).isEqualTo("Recovered");
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void returnsLastRetryableFailureWhenRetriesAreExhausted() {
        AtomicInteger attempts = new AtomicInteger();
        SourceHttpClient client = request -> {
            attempts.incrementAndGet();
            return new SourceHttpResponse(503, Map.of(), new byte[0]);
        };
        FetchOrchestrator orchestrator = orchestratorWithSources(
                List.of(source("rss-a", SourceType.RSS, true, 2)),
                client
        );

        FetchOrchestrationResult result = orchestrator.fetch(new FetchNewsCommand(List.of("rss-a"), null, null, null, 20));

        assertThat(attempts).hasValue(3);
        assertThat(result.status()).isEqualTo(FetchStatus.FAILED);
        assertThat(result.items()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().getFirst().code()).isEqualTo("HTTP_STATUS");
    }

    @Test
    void doesNotRetryNonRetryableSourceFailures() {
        AtomicInteger attempts = new AtomicInteger();
        SourceHttpClient client = request -> {
            attempts.incrementAndGet();
            return new SourceHttpResponse(200, Map.of(), "<rss>".getBytes(StandardCharsets.UTF_8));
        };
        FetchOrchestrator orchestrator = orchestratorWithSources(
                List.of(source("rss-a", SourceType.RSS, true, 3)),
                client
        );

        FetchOrchestrationResult result = orchestrator.fetch(new FetchNewsCommand(List.of("rss-a"), null, null, null, 20));

        assertThat(attempts).hasValue(1);
        assertThat(result.status()).isEqualTo(FetchStatus.FAILED);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().getFirst().code()).isEqualTo("RSS_PARSE_ERROR");
    }

    @Test
    void usesFallbackSourceWhenPrimarySourceFails() {
        SourceHttpClient client = request -> {
            if (request.url().contains("rss-primary")) {
                return new SourceHttpResponse(500, Map.of(), new byte[0]);
            }
            return new SourceHttpResponse(200, Map.of(), singleItemRssFixture("Fallback item").getBytes(StandardCharsets.UTF_8));
        };
        FetchOrchestrator orchestrator = orchestratorWithSources(
                List.of(
                        source("rss-primary", SourceType.RSS, true, 0, List.of("rss-fallback")),
                        source("rss-fallback", SourceType.RSS, true)
                ),
                client
        );

        FetchOrchestrationResult result = orchestrator.fetch(new FetchNewsCommand(List.of("rss-primary"), null, null, null, 20));

        assertThat(result.status()).isEqualTo(FetchStatus.PARTIAL);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().title()).isEqualTo("Fallback item");
        assertThat(result.items().getFirst().sourceId()).isEqualTo("rss-fallback");
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().getFirst().sourceId()).isEqualTo("rss-primary");
        assertThat(result.errors().getFirst().code()).isEqualTo("HTTP_STATUS");
    }

    @Test
    void usesFallbackOnlyAfterPrimaryRetriesAreExhausted() {
        AtomicInteger primaryAttempts = new AtomicInteger();
        AtomicInteger fallbackAttempts = new AtomicInteger();
        SourceHttpClient client = request -> {
            if (request.url().contains("rss-primary")) {
                primaryAttempts.incrementAndGet();
                return new SourceHttpResponse(503, Map.of(), new byte[0]);
            }
            fallbackAttempts.incrementAndGet();
            return new SourceHttpResponse(200, Map.of(), singleItemRssFixture("Fallback item").getBytes(StandardCharsets.UTF_8));
        };
        FetchOrchestrator orchestrator = orchestratorWithSources(
                List.of(
                        source("rss-primary", SourceType.RSS, true, 2, List.of("rss-fallback")),
                        source("rss-fallback", SourceType.RSS, true)
                ),
                client
        );

        FetchOrchestrationResult result = orchestrator.fetch(new FetchNewsCommand(List.of("rss-primary"), null, null, null, 20));

        assertThat(primaryAttempts).hasValue(3);
        assertThat(fallbackAttempts).hasValue(1);
        assertThat(result.status()).isEqualTo(FetchStatus.PARTIAL);
        assertThat(result.items()).hasSize(1);
        assertThat(result.errors()).hasSize(1);
    }

    @Test
    void returnsFallbackSelectionErrorsWhenFallbackSourcesAreInvalid() {
        FetchOrchestrator orchestrator = orchestratorWithSources(
                List.of(source("rss-primary", SourceType.RSS, true, 0, List.of("rss-primary", "missing-fallback"))),
                request -> new SourceHttpResponse(500, Map.of(), new byte[0])
        );

        FetchOrchestrationResult result = orchestrator.fetch(new FetchNewsCommand(List.of("rss-primary"), null, null, null, 20));

        assertThat(result.status()).isEqualTo(FetchStatus.FAILED);
        assertThat(result.items()).isEmpty();
        assertThat(result.errors()).extracting("code")
                .containsExactly("HTTP_STATUS", "FALLBACK_SOURCE_INVALID", "FALLBACK_SOURCE_NOT_FOUND");
    }

    @Test
    void triesNextFallbackWhenEarlierFallbackFails() {
        SourceHttpClient client = request -> {
            if (request.url().contains("rss-final")) {
                return new SourceHttpResponse(200, Map.of(), singleItemRssFixture("Final fallback").getBytes(StandardCharsets.UTF_8));
            }
            return new SourceHttpResponse(500, Map.of(), new byte[0]);
        };
        FetchOrchestrator orchestrator = orchestratorWithSources(
                List.of(
                        source("rss-primary", SourceType.RSS, true, 0, List.of("rss-bad", "rss-final")),
                        source("rss-bad", SourceType.RSS, true),
                        source("rss-final", SourceType.RSS, true)
                ),
                client
        );

        FetchOrchestrationResult result = orchestrator.fetch(new FetchNewsCommand(List.of("rss-primary"), null, null, null, 20));

        assertThat(result.status()).isEqualTo(FetchStatus.PARTIAL);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().sourceId()).isEqualTo("rss-final");
        assertThat(result.errors()).extracting("sourceId").containsExactly("rss-primary", "rss-bad");
    }

    private static FetchOrchestrator orchestratorWithSources(List<NewsFetchProperties.Source> sources, SourceHttpClient client) {
        SourceConfigRegistry registry = new SourceConfigRegistry(
                new NewsFetchProperties(sources),
                new SourceConfigValidator(new SourceUrlValidator())
        );
        return new FetchOrchestrator(
                registry,
                List.of(new RssSourceAdapter(client)),
                new NewsItemNormalizer(),
                new NewsItemDeduplicator()
        );
    }

    private static NewsFetchProperties.Source source(String id, SourceType type, boolean enabled) {
        return source(id, type, enabled, 0);
    }

    private static NewsFetchProperties.Source source(String id, SourceType type, boolean enabled, int retryCount) {
        return source(id, type, enabled, retryCount, List.of());
    }

    private static NewsFetchProperties.Source source(
            String id,
            SourceType type,
            boolean enabled,
            int retryCount,
            List<String> fallbackSourceIds
    ) {
        return new NewsFetchProperties.Source(
                id,
                id,
                type,
                enabled,
                100,
                null,
                null,
                null,
                "https://example.com/" + id + ".xml",
                "GET",
                Map.of(),
                Map.of(),
                5000,
                1048576,
                retryCount,
                fallbackSourceIds,
                Map.of(),
                null,
                null
        );
    }

    private static SourceHttpClient successClient(String body) {
        return request -> new SourceHttpResponse(200, Map.of(), body.getBytes(StandardCharsets.UTF_8));
    }

    private static String singleItemRssFixture(String title) {
        return singleItemWithUrl(title, "https://example.com/news/" + title);
    }

    private static String singleItemWithUrl(String title, String url) {
        return """
                <?xml version="1.0" encoding="UTF-8" ?>
                <rss version="2.0">
                  <channel>
                    <title>Fixture Feed</title>
                    <item>
                      <title>%s</title>
                      <link>%s</link>
                    </item>
                  </channel>
                </rss>
                """.formatted(title, url);
    }

    private static String twoItemRssFixture() {
        return """
                <?xml version="1.0" encoding="UTF-8" ?>
                <rss version="2.0">
                  <channel>
                    <title>Fixture Feed</title>
                    <item>
                      <title>First item</title>
                      <link>https://example.com/news/1</link>
                    </item>
                    <item>
                      <title>Second item</title>
                      <link>https://example.com/news/2</link>
                    </item>
                  </channel>
                </rss>
                """;
    }
}
