package com.vanlo.newsfetch.infrastructure;

public class SourceHttpClientException extends RuntimeException {

    public SourceHttpClientException(String message) {
        super(message);
    }

    public SourceHttpClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
