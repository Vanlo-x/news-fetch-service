package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.domain.NewsItem;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class NewsItemSorter {

    private static final int UNKNOWN_SOURCE_ORDER = Integer.MAX_VALUE;

    public List<NewsItem> sort(List<NewsItem> items, Map<String, Integer> sourceOrder) {
        Map<String, Integer> safeSourceOrder = sourceOrder == null ? Map.of() : sourceOrder;
        return items.stream()
                .sorted(comparator(safeSourceOrder))
                .toList();
    }

    private static Comparator<NewsItem> comparator(Map<String, Integer> sourceOrder) {
        return Comparator
                .comparing(NewsItem::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparingInt(item -> sourceOrder.getOrDefault(item.sourceId(), UNKNOWN_SOURCE_ORDER));
    }
}
