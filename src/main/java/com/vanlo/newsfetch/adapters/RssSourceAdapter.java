package com.vanlo.newsfetch.adapters;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.vanlo.newsfetch.domain.FetchError;
import com.vanlo.newsfetch.domain.NewsItem;
import com.vanlo.newsfetch.domain.SourceConfig;
import com.vanlo.newsfetch.infrastructure.SourceHttpClient;
import com.vanlo.newsfetch.infrastructure.SourceHttpClientException;
import com.vanlo.newsfetch.infrastructure.SourceHttpRequest;
import com.vanlo.newsfetch.infrastructure.SourceHttpResponse;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RssSourceAdapter {

    private static final String STAGE_FETCH = "FETCH";
    private static final String STAGE_PARSE = "PARSE";

    private final SourceHttpClient sourceHttpClient;

    public RssSourceAdapter(SourceHttpClient sourceHttpClient) {
        this.sourceHttpClient = sourceHttpClient;
    }

    public RssFetchResult fetch(SourceConfig sourceConfig) {
        Instant occurredAt = Instant.now();
        SourceHttpResponse response;
        try {
            response = sourceHttpClient.fetch(new SourceHttpRequest(
                    sourceConfig.url(),
                    sourceConfig.method(),
                    sourceConfig.headers(),
                    Duration.ofMillis(sourceConfig.timeoutMs()),
                    sourceConfig.maxResponseBytes()
            ));
        } catch (SourceHttpClientException exception) {
            return new RssFetchResult(List.of(), List.of(new FetchError(
                    sourceConfig.id(),
                    STAGE_FETCH,
                    "HTTP_CLIENT_ERROR",
                    exception.getMessage(),
                    true,
                    occurredAt
            )));
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return new RssFetchResult(List.of(), List.of(new FetchError(
                    sourceConfig.id(),
                    STAGE_FETCH,
                    "HTTP_STATUS",
                    "Source returned HTTP status " + response.statusCode(),
                    response.statusCode() >= 500,
                    occurredAt
            )));
        }

        try {
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(new ByteArrayInputStream(response.body())));
            return new RssFetchResult(toNewsItems(feed, sourceConfig, Instant.now()), List.of());
        } catch (Exception exception) {
            return new RssFetchResult(List.of(), List.of(new FetchError(
                    sourceConfig.id(),
                    STAGE_PARSE,
                    "RSS_PARSE_ERROR",
                    "Failed to parse RSS feed",
                    false,
                    Instant.now()
            )));
        }
    }

    private static List<NewsItem> toNewsItems(SyndFeed feed, SourceConfig sourceConfig, Instant fetchedAt) {
        SyndFeed safeFeed = feed == null ? new SyndFeedImpl() : feed;
        List<NewsItem> items = new ArrayList<>();

        for (SyndEntry entry : safeFeed.getEntries()) {
            String title = blankToNull(entry.getTitle());
            String url = blankToNull(entry.getLink());
            if (title == null && url == null) {
                continue;
            }

            Instant publishedAt = toInstant(entry.getPublishedDate());
            if (publishedAt == null) {
                publishedAt = toInstant(entry.getUpdatedDate());
            }

            String summary = entry.getDescription() == null ? null : blankToNull(entry.getDescription().getValue());
            String author = blankToNull(entry.getAuthor());
            String hashInput = sourceConfig.id() + "|" + nullToEmpty(url) + "|" + nullToEmpty(title)
                    + "|" + (publishedAt == null ? "" : publishedAt);
            String adapterId = sha256(hashInput);

            items.add(new NewsItem(
                    adapterId,
                    title,
                    url,
                    sourceConfig.id(),
                    sourceConfig.name(),
                    publishedAt,
                    fetchedAt,
                    summary,
                    null,
                    author,
                    null,
                    sourceConfig.category(),
                    sourceConfig.language(),
                    sourceConfig.region(),
                    List.of(),
                    adapterId,
                    null,
                    rawMetadata(safeFeed, entry)
            ));
        }

        return items;
    }

    private static Map<String, Object> rawMetadata(SyndFeed feed, SyndEntry entry) {
        Map<String, Object> raw = new LinkedHashMap<>();
        putIfPresent(raw, "feedTitle", feed.getTitle());
        putIfPresent(raw, "entryUri", entry.getUri());
        putIfPresent(raw, "entryLink", entry.getLink());
        return raw;
    }

    private static void putIfPresent(Map<String, Object> raw, String key, Object value) {
        if (value instanceof String stringValue && stringValue.isBlank()) {
            return;
        }
        if (value != null) {
            raw.put(key, value);
        }
    }

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
