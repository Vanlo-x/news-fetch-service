package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.api.FetchNewsRequest;
import com.vanlo.newsfetch.api.FetchNewsResponse;
import org.springframework.stereotype.Service;

@Service
public class FetchNewsUseCase {

    private static final int DEFAULT_LIMIT = 20;

    private final FetchOrchestrator fetchOrchestrator;

    public FetchNewsUseCase(FetchOrchestrator fetchOrchestrator) {
        this.fetchOrchestrator = fetchOrchestrator;
    }

    public FetchNewsResponse fetch(FetchNewsRequest request) {
        FetchNewsRequest safeRequest = request == null ? new FetchNewsRequest(null, null, null, null, null) : request;
        int limit = safeRequest.limit() == null ? DEFAULT_LIMIT : safeRequest.limit();
        FetchOrchestrationResult result = fetchOrchestrator.fetch(new FetchNewsCommand(
                safeRequest.sourceIds(),
                safeRequest.category(),
                safeRequest.language(),
                safeRequest.region(),
                limit
        ));

        return new FetchNewsResponse(result.status(), result.items(), result.errors());
    }
}
