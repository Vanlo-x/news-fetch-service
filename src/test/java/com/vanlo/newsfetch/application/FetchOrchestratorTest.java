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
                0,
                List.of(),
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
