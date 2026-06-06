package com.vanlo.newsfetch.api;

public class SourceStatusNotFoundException extends RuntimeException {

    public SourceStatusNotFoundException(String sourceId) {
        super("Source status not found: " + sourceId);
    }
}
