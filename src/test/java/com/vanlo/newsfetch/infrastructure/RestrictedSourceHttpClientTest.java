package com.vanlo.newsfetch.infrastructure;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestrictedSourceHttpClientTest {

    private HttpServer server;
    private RestrictedSourceHttpClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new RestrictedSourceHttpClient(new AllowAllSourceUrlValidator());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchesResponseBodyAndStatus() {
        server.createContext("/feed", exchange -> writeResponse(exchange, 200, "hello"));

        SourceHttpResponse response = client.fetch(new SourceHttpRequest(
                baseUrl + "/feed",
                "GET",
                Map.of("User-Agent", "news-fetch-service-test"),
                Duration.ofSeconds(2),
                1024
        ));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void filtersSensitiveRequestHeaders() {
        server.createContext("/headers", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String cookie = exchange.getRequestHeaders().getFirst("Cookie");
            String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");

            writeResponse(exchange, 200, authorization + "|" + cookie + "|" + userAgent);
        });

        SourceHttpResponse response = client.fetch(new SourceHttpRequest(
                baseUrl + "/headers",
                "GET",
                Map.of(
                        "Authorization", "secret",
                        "Cookie", "session=secret",
                        "User-Agent", "news-fetch-service-test"
                ),
                Duration.ofSeconds(2),
                1024
        ));

        assertThat(new String(response.body(), StandardCharsets.UTF_8))
                .isEqualTo("null|null|news-fetch-service-test");
    }

    @Test
    void rejectsUnsafeUrlBeforeSendingRequest() {
        RestrictedSourceHttpClient guardedClient = new RestrictedSourceHttpClient(new SourceUrlValidator());

        assertThatThrownBy(() -> guardedClient.fetch(new SourceHttpRequest(
                baseUrl + "/feed",
                "GET",
                Map.of(),
                Duration.ofSeconds(2),
                1024
        )))
                .isInstanceOf(SourceHttpClientException.class)
                .hasMessageContaining("Unsafe source URL");
    }

    @Test
    void failsWhenResponseExceedsSizeLimit() {
        server.createContext("/large", exchange -> writeResponse(exchange, 200, "too-large"));

        assertThatThrownBy(() -> client.fetch(new SourceHttpRequest(
                baseUrl + "/large",
                "GET",
                Map.of(),
                Duration.ofSeconds(2),
                4
        )))
                .isInstanceOf(SourceHttpClientException.class)
                .hasMessageContaining("exceeded size limit");
    }

    @Test
    void doesNotFollowRedirects() {
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", baseUrl + "/target");
            writeResponse(exchange, 302, "");
        });
        server.createContext("/target", exchange -> writeResponse(exchange, 200, "target"));

        SourceHttpResponse response = client.fetch(new SourceHttpRequest(
                baseUrl + "/redirect",
                "GET",
                Map.of(),
                Duration.ofSeconds(2),
                1024
        ));

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.body()).isEmpty();
    }

    @Test
    void rejectsUnsupportedHttpMethod() {
        assertThatThrownBy(() -> client.fetch(new SourceHttpRequest(
                baseUrl + "/feed",
                "PUT",
                Map.of(),
                Duration.ofSeconds(2),
                1024
        )))
                .isInstanceOf(SourceHttpClientException.class)
                .hasMessageContaining("Unsupported HTTP method");
    }

    private static void writeResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (exchange; var responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }

    private static class AllowAllSourceUrlValidator extends SourceUrlValidator {

        @Override
        public SourceUrlValidationResult validate(String url) {
            return SourceUrlValidationResult.allow();
        }
    }
}
