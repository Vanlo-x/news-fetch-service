package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.domain.NewsItem;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class NewsItemDeduplicator {

    public List<NewsItem> deduplicate(List<NewsItem> items) {
        Map<String, NewsItem> byFingerprint = new LinkedHashMap<>();
        for (NewsItem item : items) {
            byFingerprint.putIfAbsent(item.fingerprint(), item);
        }
        return List.copyOf(byFingerprint.values());
    }
}
