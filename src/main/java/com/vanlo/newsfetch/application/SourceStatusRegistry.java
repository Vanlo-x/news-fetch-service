package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.domain.SourceStatus;

import java.util.List;
import java.util.Optional;

public interface SourceStatusRegistry {

    void record(SourceStatusUpdate update);

    List<SourceStatus> allStatuses();

    Optional<SourceStatus> findBySourceId(String sourceId);
}
