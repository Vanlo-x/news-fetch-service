package com.vanlo.newsfetch.infrastructure;

import java.util.List;
import java.util.Map;

public record SourceHttpResponse(
        int statusCode,
        Map<String, List<String>> headers,
        byte[] body
) {

    public SourceHttpResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
