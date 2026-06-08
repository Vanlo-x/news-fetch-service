package com.vanlo.newsfetch.infrastructure;

public class SourceHttpClientException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public SourceHttpClientException(String message) {
        this("HTTP_CLIENT_ERROR", message, true);
    }

    public SourceHttpClientException(String message, Throwable cause) {
        this("HTTP_CLIENT_ERROR", message, true, cause);
    }

    public SourceHttpClientException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public SourceHttpClientException(String code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
