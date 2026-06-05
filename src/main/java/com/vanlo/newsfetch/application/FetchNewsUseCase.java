package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.api.FetchNewsRequest;
import com.vanlo.newsfetch.api.FetchNewsResponse;
import com.vanlo.newsfetch.domain.FetchStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FetchNewsUseCase {

    public FetchNewsResponse fetch(FetchNewsRequest request) {
        return new FetchNewsResponse(FetchStatus.OK, List.of(), List.of());
    }
}
