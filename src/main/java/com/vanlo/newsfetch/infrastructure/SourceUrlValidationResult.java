package com.vanlo.newsfetch.infrastructure;

public record SourceUrlValidationResult(
        boolean allowed,
        String reason
) {

    public static SourceUrlValidationResult allow() {
        return new SourceUrlValidationResult(true, "allowed");
    }

    public static SourceUrlValidationResult rejected(String reason) {
        return new SourceUrlValidationResult(false, reason);
    }
}
