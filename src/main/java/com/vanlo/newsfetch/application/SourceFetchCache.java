package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.adapters.SourceFetchResult;

import java.time.Duration;
import java.util.Optional;

public interface SourceFetchCache {

    Optional<SourceFetchResult> get(String sourceId);

    void put(String sourceId, SourceFetchResult result, Duration ttl);
}
