package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.adapters.SourceFetchResult;

import java.time.Duration;
import java.util.Optional;

public interface SourceFetchCache {

    Optional<CachedSourceFetchResult> get(String sourceId);

    Optional<CachedSourceFetchResult> getStale(String sourceId);

    void put(String sourceId, SourceFetchResult result, Duration ttl);
}
