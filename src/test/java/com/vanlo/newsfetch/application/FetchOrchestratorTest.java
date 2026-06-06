package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.adapters.RssSourceAdapter;
import com.vanlo.newsfetch.config.NewsFetchProperties;
import com.vanlo.newsfetch.config.SourceConfigRegistry;
import com.vanlo.newsfetch.config.SourceConfigValidator;
import com.vanlo.newsfetch.domain.FetchStatus;
import com.vanlo.newsfetch.domain.SourceHealth;
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

    @Test
    void cachesSuccessfulSourceResultAndSkipsHttpOnCacheHit() {
        AtomicInteger attempts = new AtomicInteger();
        SourceHttpClient client = request -> new SourceHttpResponse(
                200,
                Map.of(),
                singleItemRssFixture("Attempt " + attempts.incrementAndGet()).getBytes(StandardCharsets.UTF_8)
        );
        TestOrchestrator testOrchestrator = testOrchestratorWithSources(
                List.of(source("rss-a", SourceType.RSS, true, 0, List.of(), 60L)),
                client
        );
        FetchOrchestrator orchestrator = testOrchestrator.orchestrator();

        FetchOrchestrationResult firstResult = orchestrator.fetch(new FetchNewsCommand(List.of("rss-a"), null, null, null, 20));
        FetchOrchestrationResult secondResult = orchestrator.fetch(new FetchNewsCommand(List.of("rss-a"), null, null, null, 20));

        assertThat(attempts).hasValue(1);
        assertThat(firstResult.items().getFirst().title()).isEqualTo("Attempt 1");
        assertThat(secondResult.items().getFirst().title()).isEqualTo("Attempt 1");
        assertThat(secondResult.errors()).isEmpty();
        assertThat(testOrchestrator.sourceStatusRegistry().findBySourceId("rss-a").orElseThrow().lastCacheHit()).isTrue();
    }

    @Test
    void recordsSourceStatusForSuccessfulFetch() {
        TestOrchestrator testOrchestrator = testOrchestratorWithSources(
                List.of(source("rss-a", SourceType.RSS, true, 0, List.of(), 60L)),
                successClient(singleItemRssFixture("Status item"))
        );

        testOrchestrator.orchestrator().fetch(new FetchNewsCommand(List.of("rss-a"), null, null, null, 20));

        var status = testOrchestrator.sourceStatusRegistry().findBySourceId("rss-a").orElseThrow();
        assertThat(status.health()).isEqualTo(SourceHealth.OK);
        assertThat(status.lastSuccessAt()).isNotNull();
        assertThat(status.lastFailureAt()).isNull();
        assertThat(status.lastItemCount()).isEqualTo(1);
        assertThat(status.lastResolvedSourceId()).isEqualTo("rss-a");
    }

    @Test
    void doesNotCacheFailedSourceResult() {
        AtomicInteger attempts = new AtomicInteger();
        FetchOrchestrator orchestrator = orchestratorWithSources(
                List.of(source("rss-a", SourceType.RSS, true, 0, List.of(), 60L)),
                request -> {
                    attempts.incrementAndGet();
                    return new SourceHttpResponse(500, Map.of(), new byte[0]);
                }
        );

        FetchOrchestrationResult firstResult = orchestrator.fetch(new FetchNewsCommand(List.of("rss-a"), null, null, null, 20));
        FetchOrchestrationResult secondResult = orchestrator.fetch(new FetchNewsCommand(List.of("rss-a"), null, null, null, 20));

        assertThat(attempts).hasValue(2);
        assertThat(firstResult.status()).isEqualTo(FetchStatus.FAILED);
        assertThat(secondResult.status()).isEqualTo(FetchStatus.FAILED);
    }

    @Test
    void fallbackSourceUsesItsOwnCacheAcrossPrimaryFailures() {
        AtomicInteger primaryAttempts = new AtomicInteger();
        AtomicInteger fallbackAttempts = new AtomicInteger();
        SourceHttpClient client = request -> {
            if (request.url().contains("rss-primary")) {
                primaryAttempts.incrementAndGet();
                return new SourceHttpResponse(500, Map.of(), new byte[0]);
            }
            fallbackAttempts.incrementAndGet();
            return new SourceHttpResponse(200, Map.of(), singleItemRssFixture("Cached fallback").getBytes(StandardCharsets.UTF_8));
        };
        FetchOrchestrator orchestrator = orchestratorWithSources(
                List.of(
                        source("rss-primary", SourceType.RSS, true, 0, List.of("rss-fallback")),
                        source("rss-fallback", SourceType.RSS, true, 0, List.of(), 60L)
                ),
                client
        );

        FetchOrchestrationResult firstResult = orchestrator.fetch(new FetchNewsCommand(List.of("rss-primary"), null, null, null, 20));
        FetchOrchestrationResult secondResult = orchestrator.fetch(new FetchNewsCommand(List.of("rss-primary"), null, null, null, 20));

        assertThat(primaryAttempts).hasValue(2);
        assertThat(fallbackAttempts).hasValue(1);
        assertThat(firstResult.status()).isEqualTo(FetchStatus.PARTIAL);
        assertThat(secondResult.status()).isEqualTo(FetchStatus.PARTIAL);
        assertThat(secondResult.items().getFirst().title()).isEqualTo("Cached fallback");
    }

    @Test
    void recordsSourceStatusForFallbackSuccess() {
        SourceHttpClient client = request -> {
            if (request.url().contains("rss-primary")) {
                return new SourceHttpResponse(500, Map.of(), new byte[0]);
            }
            return new SourceHttpResponse(200, Map.of(), singleItemRssFixture("Fallback item").getBytes(StandardCharsets.UTF_8));
        };
        TestOrchestrator testOrchestrator = testOrchestratorWithSources(
                List.of(
                        source("rss-primary", SourceType.RSS, true, 0, List.of("rss-fallback")),
                        source("rss-fallback", SourceType.RSS, true)
                ),
                client
        );

        testOrchestrator.orchestrator().fetch(new FetchNewsCommand(List.of("rss-primary"), null, null, null, 20));

        var primaryStatus = testOrchestrator.sourceStatusRegistry().findBySourceId("rss-primary").orElseThrow();
        var fallbackStatus = testOrchestrator.sourceStatusRegistry().findBySourceId("rss-fallback").orElseThrow();
        assertThat(primaryStatus.health()).isEqualTo(SourceHealth.DEGRADED);
        assertThat(primaryStatus.lastFallbackUsed()).isTrue();
        assertThat(primaryStatus.lastResolvedSourceId()).isEqualTo("rss-fallback");
        assertThat(primaryStatus.lastErrorCode()).isEqualTo("HTTP_STATUS");
        assertThat(fallbackStatus.health()).isEqualTo(SourceHealth.OK);
    }

    private static FetchOrchestrator orchestratorWithSources(List<NewsFetchProperties.Source> sources, SourceHttpClient client) {
        return testOrchestratorWithSources(sources, client).orchestrator();
    }

    private static TestOrchestrator testOrchestratorWithSources(List<NewsFetchProperties.Source> sources, SourceHttpClient client) {
        SourceConfigRegistry registry = new SourceConfigRegistry(
                new NewsFetchProperties(sources),
                new SourceConfigValidator(new SourceUrlValidator())
        );
        InMemorySourceStatusRegistry sourceStatusRegistry = new InMemorySourceStatusRegistry(registry);
        FetchOrchestrator orchestrator = new FetchOrchestrator(
                registry,
                List.of(new RssSourceAdapter(client)),
                new NewsItemNormalizer(),
                new NewsItemDeduplicator(),
                new InMemorySourceFetchCache(),
                sourceStatusRegistry
        );
        return new TestOrchestrator(orchestrator, sourceStatusRegistry);
    }

    private record TestOrchestrator(
            FetchOrchestrator orchestrator,
            InMemorySourceStatusRegistry sourceStatusRegistry
    ) {
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
        return source(id, type, enabled, retryCount, fallbackSourceIds, null);
    }

    private static NewsFetchProperties.Source source(
            String id,
            SourceType type,
            boolean enabled,
            int retryCount,
            List<String> fallbackSourceIds,
            Long cacheTtlSeconds
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
                cacheTtlSeconds
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
