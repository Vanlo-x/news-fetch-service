package com.vanlo.newsfetch.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpConnectTimeoutException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class RestrictedSourceHttpClient implements SourceHttpClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final SourceUrlValidator sourceUrlValidator;

    @Autowired
    public RestrictedSourceHttpClient(SourceUrlValidator sourceUrlValidator) {
        this(defaultHttpClient(), sourceUrlValidator);
    }

    RestrictedSourceHttpClient(HttpClient httpClient, SourceUrlValidator sourceUrlValidator) {
        this.httpClient = httpClient;
        this.sourceUrlValidator = sourceUrlValidator;
    }

    @Override
    public SourceHttpResponse fetch(SourceHttpRequest request) {
        SourceUrlValidationResult validation = sourceUrlValidator.validate(request.url());
        if (!validation.allowed()) {
            throw new SourceHttpClientException("UNSAFE_URL", "Unsafe source URL: " + validation.reason(), false);
        }

        HttpRequest httpRequest = buildRequest(request);
        try {
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body = readLimited(response.body(), request.maxResponseBytes());
            return new SourceHttpResponse(response.statusCode(), response.headers().map(), body);
        } catch (HttpConnectTimeoutException exception) {
            throw new SourceHttpClientException("CONNECT_TIMEOUT", "Source HTTP connect timed out", true, exception);
        } catch (HttpTimeoutException exception) {
            throw new SourceHttpClientException("READ_TIMEOUT", "Source HTTP request timed out", true, exception);
        } catch (IOException exception) {
            throw new SourceHttpClientException("HTTP_IO_ERROR", "Source HTTP request failed: " + exception.getMessage(), true, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SourceHttpClientException("HTTP_INTERRUPTED", "Source HTTP request interrupted", true, exception);
        }
    }

    private static HttpClient defaultHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER);

        proxySelectorFromEnvironment().ifPresent(builder::proxy);
        return builder.build();
    }

    private static Optional<ProxySelector> proxySelectorFromEnvironment() {
        String proxy = firstPresent("HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy");
        if (proxy == null || proxy.isBlank()) {
            return Optional.empty();
        }

        URI proxyUri = URI.create(proxy);
        String host = proxyUri.getHost();
        int port = proxyUri.getPort();
        if (host == null || port <= 0) {
            return Optional.empty();
        }

        List<String> noProxyHosts = noProxyHosts();
        ProxySelector proxySelector = ProxySelector.of(new InetSocketAddress(host, port));
        return Optional.of(new NoProxyAwareProxySelector(proxySelector, noProxyHosts));
    }

    private static String firstPresent(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static List<String> noProxyHosts() {
        String noProxy = firstPresent("NO_PROXY", "no_proxy");
        if (noProxy == null || noProxy.isBlank()) {
            return List.of();
        }
        return Arrays.stream(noProxy.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
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
            throw new SourceHttpClientException("UNSUPPORTED_HTTP_METHOD", "Unsupported HTTP method: " + method, false);
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
                    throw new SourceHttpClientException("RESPONSE_TOO_LARGE", "Source HTTP response exceeded size limit", false);
                }
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }
}
