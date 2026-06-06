package com.vanlo.newsfetch.api;

import com.vanlo.newsfetch.infrastructure.SourceHttpClient;
import com.vanlo.newsfetch.infrastructure.SourceHttpRequest;
import com.vanlo.newsfetch.infrastructure.SourceHttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "news-fetch.sources[0].id=tech-rss",
        "news-fetch.sources[0].name=Tech RSS",
        "news-fetch.sources[0].type=RSS",
        "news-fetch.sources[0].category=technology",
        "news-fetch.sources[0].language=zh",
        "news-fetch.sources[0].region=CN",
        "news-fetch.sources[0].url=https://example.com/rss.xml"
})
@AutoConfigureMockMvc
class NewsFetchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fetchReturnsRealRssItems() throws Exception {
        mockMvc.perform(post("/v1/news/fetch")
                        .contentType("application/json")
                        .content("""
                                {
                                  "sourceIds": ["tech-rss"],
                                  "category": "technology",
                                  "language": "zh",
                                  "region": "CN",
                                  "limit": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.items[0].title").value("First item"))
                .andExpect(jsonPath("$.items[0].url").value("https://example.com/news/1"))
                .andExpect(jsonPath("$.items[0].sourceId").value("tech-rss"))
                .andExpect(jsonPath("$.items[0].sourceName").value("Tech RSS"))
                .andExpect(jsonPath("$.items[0].category").value("technology"))
                .andExpect(jsonPath("$.items[0].language").value("zh"))
                .andExpect(jsonPath("$.items[0].region").value("CN"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void fetchAllowsOptionalFilters() throws Exception {
        mockMvc.perform(post("/v1/news/fetch")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.items[0].title").value("First item"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void fetchReturnsErrorForUnknownSource() throws Exception {
        mockMvc.perform(post("/v1/news/fetch")
                        .contentType("application/json")
                        .content("""
                                {
                                  "sourceIds": ["missing-source"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.errors[0].sourceId").value("missing-source"))
                .andExpect(jsonPath("$.errors[0].code").value("SOURCE_NOT_FOUND"));
    }

    @Test
    void fetchRejectsInvalidLimit() throws Exception {
        mockMvc.perform(post("/v1/news/fetch")
                        .contentType("application/json")
                        .content("""
                                {
                                  "limit": 101
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
    }

    @Test
    void fetchRejectsBlankSourceId() throws Exception {
        mockMvc.perform(post("/v1/news/fetch")
                        .contentType("application/json")
                        .content("""
                                {
                                  "sourceIds": [" "]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("sourceIds[0]"));
    }

    @Test
    void fetchRejectsInvalidLanguageAndRegionFormat() throws Exception {
        mockMvc.perform(post("/v1/news/fetch")
                        .contentType("application/json")
                        .content("""
                                {
                                  "language": "ZH",
                                  "region": "cn"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("language"))
                .andExpect(jsonPath("$.errors[1].field").value("region"));
    }

    @Test
    void fetchRejectsMalformedBody() throws Exception {
        mockMvc.perform(post("/v1/news/fetch")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void sourceStatusReturnsUnknownBeforeFetch() throws Exception {
        mockMvc.perform(get("/v1/news/sources/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceId").value("tech-rss"))
                .andExpect(jsonPath("$[0].health").value("UNKNOWN"))
                .andExpect(jsonPath("$[0].lastFetchAt").doesNotExist());
    }

    @Test
    void sourceStatusReflectsSuccessfulFetch() throws Exception {
        mockMvc.perform(post("/v1/news/fetch")
                        .contentType("application/json")
                        .content("""
                                {
                                  "sourceIds": ["tech-rss"]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/news/sources/tech-rss/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value("tech-rss"))
                .andExpect(jsonPath("$.sourceName").value("Tech RSS"))
                .andExpect(jsonPath("$.sourceType").value("RSS"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.health").value("OK"))
                .andExpect(jsonPath("$.lastItemCount").value(1))
                .andExpect(jsonPath("$.lastCacheHit").value(false))
                .andExpect(jsonPath("$.lastFallbackUsed").value(false))
                .andExpect(jsonPath("$.lastResolvedSourceId").value("tech-rss"));
    }

    @Test
    void sourceStatusReturnsNotFoundForUnknownSource() throws Exception {
        mockMvc.perform(get("/v1/news/sources/missing/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"));
    }

    @TestConfiguration
    static class TestHttpClientConfiguration {

        @Bean
        @Primary
        SourceHttpClient sourceHttpClient() {
            return request -> new SourceHttpResponse(
                    200,
                    Map.of(),
                    rssFixture().getBytes(StandardCharsets.UTF_8)
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
}
