package com.vanlo.newsfetch.api;

import com.vanlo.newsfetch.application.FetchNewsUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/news")
public class NewsFetchController {

    private final FetchNewsUseCase fetchNewsUseCase;

    public NewsFetchController(FetchNewsUseCase fetchNewsUseCase) {
        this.fetchNewsUseCase = fetchNewsUseCase;
    }

    @PostMapping("/fetch")
    public FetchNewsResponse fetch(@Valid @RequestBody FetchNewsRequest request) {
        return fetchNewsUseCase.fetch(request);
    }
}
