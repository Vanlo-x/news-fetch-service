package com.vanlo.newsfetch.adapters;

import com.vanlo.newsfetch.domain.SourceConfig;
import com.vanlo.newsfetch.domain.SourceType;
import com.vanlo.newsfetch.infrastructure.SourceHttpClient;
import com.vanlo.newsfetch.infrastructure.SourceHttpClientException;
import com.vanlo.newsfetch.infrastructure.SourceHttpResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RssSourceAdapterTest {

    @Test
    void parsesRssItemsIntoNewsItems() {
        RssSourceAdapter adapter = new RssSourceAdapter(successClient(rssFixture()));

        RssFetchResult result = adapter.fetch(sourceConfig("rss-source"));

        assertThat(result.errors()).isEmpty();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().title()).isEqualTo("First item");
        assertThat(result.items().getFirst().url()).isEqualTo("https://example.com/news/1");
        assertThat(result.items().getFirst().sourceId()).isEqualTo("rss-source");
        assertThat(result.items().getFirst().summary()).isEqualTo("First summary");
        assertThat(result.items().getFirst().fingerprint()).isNotBlank();
        assertThat(result.items().getFirst().raw()).containsEntry("feedTitle", "Fixture Feed");
    }

    @Test
    void returnsErrorForNonSuccessfulHttpStatus() {
        RssSourceAdapter adapter = new RssSourceAdapter(request -> new SourceHttpResponse(503, Map.of(), new byte[0]));

        RssFetchResult result = adapter.fetch(sourceConfig("rss-source"));

        assertThat(result.items()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().getFirst().code()).isEqualTo("HTTP_STATUS");
        assertThat(result.errors().getFirst().retryable()).isTrue();
    }

    @Test
    void returnsErrorForHttpClientFailure() {
        RssSourceAdapter adapter = new RssSourceAdapter(request -> {
            throw new SourceHttpClientException("network failed");
        });

        RssFetchResult result = adapter.fetch(sourceConfig("rss-source"));

        assertThat(result.items()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().getFirst().code()).isEqualTo("HTTP_CLIENT_ERROR");
    }

    @Test
    void returnsErrorForInvalidXml() {
        RssSourceAdapter adapter = new RssSourceAdapter(successClient("<rss>"));

        RssFetchResult result = adapter.fetch(sourceConfig("rss-source"));

        assertThat(result.items()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().getFirst().code()).isEqualTo("RSS_PARSE_ERROR");
    }

    @Test
    void returnsEmptyItemsForEmptyFeed() {
        RssSourceAdapter adapter = new RssSourceAdapter(successClient("""
                <?xml version="1.0" encoding="UTF-8" ?>
                <rss version="2.0">
                  <channel>
                    <title>Empty Feed</title>
                  </channel>
                </rss>
                """));

        RssFetchResult result = adapter.fetch(sourceConfig("rss-source"));

        assertThat(result.items()).isEmpty();
        assertThat(result.errors()).isEmpty();
    }

    private static SourceHttpClient successClient(String body) {
        return request -> new SourceHttpResponse(200, Map.of(), body.getBytes(StandardCharsets.UTF_8));
    }

    private static SourceConfig sourceConfig(String id) {
        return new SourceConfig(
                id,
                "RSS Source",
                SourceType.RSS,
                true,
                100,
                "technology",
                "zh",
                "CN",
                "https://example.com/rss.xml",
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

    private static String rssFixture() {
        return """
                <?xml version="1.0" encoding="UTF-8" ?>
                <rss version="2.0">
                  <channel>
                    <title>Fixture Feed</title>
                    <item>
                      <title>First item</title>
                      <link>https://example.com/news/1</link>
                      <description>First summary</description>
                      <author>editor@example.com</author>
                      <pubDate>Sat, 06 Jun 2026 06:00:00 GMT</pubDate>
                      <guid>item-1</guid>
                    </item>
                  </channel>
                </rss>
                """;
    }
}
