package com.vanlo.newsfetch.api;

import com.vanlo.newsfetch.application.SourceStatusRegistry;
import com.vanlo.newsfetch.domain.SourceStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/news/sources")
public class SourceStatusController {

    private final SourceStatusRegistry sourceStatusRegistry;

    public SourceStatusController(SourceStatusRegistry sourceStatusRegistry) {
        this.sourceStatusRegistry = sourceStatusRegistry;
    }

    @GetMapping("/status")
    public List<SourceStatus> allStatuses() {
        return sourceStatusRegistry.allStatuses();
    }

    @GetMapping("/{sourceId}/status")
    public SourceStatus status(@PathVariable String sourceId) {
        return sourceStatusRegistry.findBySourceId(sourceId)
                .orElseThrow(() -> new SourceStatusNotFoundException(sourceId));
    }
}
