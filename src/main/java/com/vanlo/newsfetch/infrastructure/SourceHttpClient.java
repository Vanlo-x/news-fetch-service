package com.vanlo.newsfetch.infrastructure;

public interface SourceHttpClient {

    SourceHttpResponse fetch(SourceHttpRequest request);
}
