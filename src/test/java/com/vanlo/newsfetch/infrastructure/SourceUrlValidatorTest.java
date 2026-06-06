package com.vanlo.newsfetch.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceUrlValidatorTest {

    private final SourceUrlValidator validator = new SourceUrlValidator();

    @Test
    void allowsPublicHttpAndHttpsUrls() {
        assertThat(validator.validate("https://example.com/rss.xml").allowed()).isTrue();
        assertThat(validator.validate("http://news.example.com/feed").allowed()).isTrue();
        assertThat(validator.validate("https://example.com:8443/feed?lang=en").allowed()).isTrue();
        assertThat(validator.validate("https://8.8.8.8/feed").allowed()).isTrue();
        assertThat(validator.validate("https://[2606:4700:4700::1111]/feed").allowed()).isTrue();
    }

    @Test
    void rejectsMalformedOrUnsupportedUrls() {
        assertThat(validator.validate(null).allowed()).isFalse();
        assertThat(validator.validate("example.com/feed").allowed()).isFalse();
        assertThat(validator.validate("http://").allowed()).isFalse();
        assertThat(validator.validate("http://[::1").allowed()).isFalse();
        assertThat(validator.validate("ftp://example.com/feed").allowed()).isFalse();
        assertThat(validator.validate("https://user:pass@example.com/feed").allowed()).isFalse();
    }

    @Test
    void rejectsLocalhostName() {
        assertThat(validator.validate("http://localhost/feed").allowed()).isFalse();
        assertThat(validator.validate("http://LOCALHOST/feed").allowed()).isFalse();
        assertThat(validator.validate("http://localhost./feed").allowed()).isFalse();
        assertThat(validator.validate("http://api.localhost/feed").allowed()).isFalse();
    }

    @Test
    void allowsNonLocalhostHostnameContainingLocalhost() {
        assertThat(validator.validate("http://localhost.example.com/feed").allowed()).isTrue();
    }

    @Test
    void rejectsUnsafeIpv4Literals() {
        assertThat(validator.validate("http://127.0.0.1/feed").allowed()).isFalse();
        assertThat(validator.validate("http://127.10.20.30/feed").allowed()).isFalse();
        assertThat(validator.validate("http://10.0.0.1/feed").allowed()).isFalse();
        assertThat(validator.validate("http://172.16.0.1/feed").allowed()).isFalse();
        assertThat(validator.validate("http://172.31.255.255/feed").allowed()).isFalse();
        assertThat(validator.validate("http://192.168.1.1/feed").allowed()).isFalse();
        assertThat(validator.validate("http://169.254.1.1/feed").allowed()).isFalse();
        assertThat(validator.validate("http://169.254.169.254/latest/meta-data").allowed()).isFalse();
        assertThat(validator.validate("http://0.0.0.0/feed").allowed()).isFalse();
    }

    @Test
    void allowsPublicIpv4Boundary() {
        assertThat(validator.validate("http://172.32.0.1/feed").allowed()).isTrue();
    }

    @Test
    void rejectsUnsafeIpv6Literals() {
        assertThat(validator.validate("http://[::1]/feed").allowed()).isFalse();
        assertThat(validator.validate("http://[::]/feed").allowed()).isFalse();
        assertThat(validator.validate("http://[fe80::1]/feed").allowed()).isFalse();
        assertThat(validator.validate("http://[fc00::1]/feed").allowed()).isFalse();
        assertThat(validator.validate("http://[fd12:3456::1]/feed").allowed()).isFalse();
        assertThat(validator.validate("http://[::ffff:127.0.0.1]/feed").allowed()).isFalse();
    }
}
