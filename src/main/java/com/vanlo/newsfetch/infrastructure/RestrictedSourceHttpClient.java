package com.vanlo.newsfetch.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

@Component
public class RestrictedSourceHttpClient implements SourceHttpClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final SourceUrlValidator sourceUrlValidator;

    @Autowired
    public RestrictedSourceHttpClient(SourceUrlValidator sourceUrlValidator) {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), sourceUrlValidator);
    }

    RestrictedSourceHttpClient(HttpClient httpClient, SourceUrlValidator sourceUrlValidator) {
        this.httpClient = httpClient;
        this.sourceUrlValidator = sourceUrlValidator;
    }

    @Override
    public SourceHttpResponse fetch(SourceHttpRequest request) {
        SourceUrlValidationResult validation = sourceUrlValidator.validate(request.url());
        if (!validation.allowed()) {
            throw new SourceHttpClientException("Unsafe source URL: " + validation.reason());
        }

        HttpRequest httpRequest = buildRequest(request);
        try {
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body = readLimited(response.body(), request.maxResponseBytes());
            return new SourceHttpResponse(response.statusCode(), response.headers().map(), body);
        } catch (IOException exception) {
            throw new SourceHttpClientException("Source HTTP request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SourceHttpClientException("Source HTTP request interrupted", exception);
        }
    }

    private static HttpRequest buildRequest(SourceHttpRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(request.url()))
                .timeout(timeoutOrDefault(request.timeout()));

        request.headers().forEach((name, value) -> {
            if (isAllowedRequestHeader(name)) {
                builder.header(name, value);
            }
        });

        String method = request.method() == null ? "GET" : request.method().toUpperCase(Locale.ROOT);
        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else if ("GET".equals(method)) {
            builder.GET();
        } else {
            throw new SourceHttpClientException("Unsupported HTTP method: " + method);
        }

        return builder.build();
    }

    private static Duration timeoutOrDefault(Duration timeout) {
        return timeout == null ? DEFAULT_TIMEOUT : timeout;
    }

    private static boolean isAllowedRequestHeader(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        String normalized = name.toLowerCase(Locale.ROOT);
        return !normalized.equals("authorization")
                && !normalized.equals("cookie")
                && !normalized.equals("proxy-authorization");
    }

    private static byte[] readLimited(InputStream inputStream, int maxResponseBytes) throws IOException {
        if (maxResponseBytes <= 0) {
            throw new SourceHttpClientException("maxResponseBytes must be positive");
        }

        try (inputStream; ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > maxResponseBytes) {
                    throw new SourceHttpClientException("Source HTTP response exceeded size limit");
                }
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }
}
