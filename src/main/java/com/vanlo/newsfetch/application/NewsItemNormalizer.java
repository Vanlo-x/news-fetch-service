package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.domain.NewsItem;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class NewsItemNormalizer {

    public NewsItem normalize(NewsItem item) {
        String title = normalizeText(item.title());
        String url = normalizeUrl(item.url());
        String summary = normalizeText(item.summary());
        String author = normalizeText(item.author());
        String content = normalizeText(item.content());
        String imageUrl = normalizeUrl(item.imageUrl());
        String fingerprint = fingerprint(title, url, item.publishedAt() == null ? null : item.publishedAt().toString());

        return new NewsItem(
                fingerprint,
                title,
                url,
                item.sourceId(),
                item.sourceName(),
                item.publishedAt(),
                item.fetchedAt(),
                summary,
                content,
                author,
                imageUrl,
                item.category(),
                item.language(),
                item.region(),
                item.tags() == null ? List.of() : List.copyOf(item.tags()),
                fingerprint,
                item.qualityScore(),
                item.raw() == null ? Map.of() : Map.copyOf(item.raw())
        );
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\u00a0', ' ')
                .trim()
                .replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizeUrl(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }

        try {
            URI uri = new URI(normalized).normalize();
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
            URI rebuilt = new URI(
                    scheme,
                    uri.getUserInfo(),
                    host,
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    null
            );
            return rebuilt.toString();
        } catch (URISyntaxException exception) {
            return normalized;
        }
    }

    private static String fingerprint(String title, String url, String publishedAt) {
        if (url != null) {
            return sha256("url|" + url);
        }
        return sha256("title|" + nullToEmpty(title).toLowerCase(Locale.ROOT) + "|" + nullToEmpty(publishedAt));
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
