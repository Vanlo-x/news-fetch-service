package com.vanlo.newsfetch.infrastructure;

import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Component
public class SourceUrlValidator {

    public SourceUrlValidationResult validate(String url) {
        if (url == null || url.isBlank()) {
            return SourceUrlValidationResult.rejected("URL is required");
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException exception) {
            return SourceUrlValidationResult.rejected("URL is malformed");
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return SourceUrlValidationResult.rejected("URL scheme must be http or https");
        }

        if (uri.getUserInfo() != null) {
            return SourceUrlValidationResult.rejected("URL must not contain user info");
        }

        String host = normalizeHost(uri.getHost());
        if (host == null || host.isBlank()) {
            return SourceUrlValidationResult.rejected("URL host is required");
        }

        if (isLocalhostName(host)) {
            return SourceUrlValidationResult.rejected("URL host must not be localhost");
        }

        if ("metadata.google.internal".equals(host)) {
            return SourceUrlValidationResult.rejected("URL host must not be a metadata service");
        }

        return validateIpLiteral(host);
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }

        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private static boolean isLocalhostName(String host) {
        return "localhost".equals(host) || host.endsWith(".localhost");
    }

    private static SourceUrlValidationResult validateIpLiteral(String host) {
        if (host.matches("[0-9.]+")) {
            return validateIpv4Literal(host);
        }

        if (host.contains(":")) {
            return validateIpv6Literal(host);
        }

        return SourceUrlValidationResult.allow();
    }

    private static SourceUrlValidationResult validateIpv4Literal(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return SourceUrlValidationResult.rejected("IPv4 host is malformed");
        }

        int[] octets = new int[4];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isBlank() || !parts[i].matches("\\d{1,3}")) {
                return SourceUrlValidationResult.rejected("IPv4 host is malformed");
            }
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] > 255) {
                return SourceUrlValidationResult.rejected("IPv4 host is malformed");
            }
        }

        return validateIpv4Octets(octets);
    }

    private static SourceUrlValidationResult validateIpv6Literal(String host) {
        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (Exception exception) {
            return SourceUrlValidationResult.rejected("IPv6 host is malformed");
        }

        if (!(address instanceof Inet6Address)) {
            byte[] bytes = address.getAddress();
            int[] octets = new int[] {
                    bytes[0] & 0xff,
                    bytes[1] & 0xff,
                    bytes[2] & 0xff,
                    bytes[3] & 0xff
            };
            return validateIpv4Octets(octets);
        }

        if (address.isAnyLocalAddress()) {
            return SourceUrlValidationResult.rejected("IPv6 host must not be unspecified");
        }
        if (address.isLoopbackAddress()) {
            return SourceUrlValidationResult.rejected("IPv6 host must not be loopback");
        }
        if (address.isLinkLocalAddress()) {
            return SourceUrlValidationResult.rejected("IPv6 host must not be link-local");
        }
        if (address.isSiteLocalAddress()) {
            return SourceUrlValidationResult.rejected("IPv6 host must not be site-local");
        }
        if (address.isMulticastAddress()) {
            return SourceUrlValidationResult.rejected("IPv6 host must not be multicast");
        }
        if (isUniqueLocalIpv6(address)) {
            return SourceUrlValidationResult.rejected("IPv6 host must not be unique-local");
        }

        return SourceUrlValidationResult.allow();
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        return (address.getAddress()[0] & 0xfe) == 0xfc;
    }

    private static SourceUrlValidationResult validateIpv4Octets(int[] octets) {
        if (octets[0] == 0) {
            return SourceUrlValidationResult.rejected("IPv4 host must not be unspecified");
        }
        if (octets[0] == 127) {
            return SourceUrlValidationResult.rejected("IPv4 host must not be loopback");
        }
        if (octets[0] == 10
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168)) {
            return SourceUrlValidationResult.rejected("IPv4 host must not be private");
        }
        if (octets[0] == 169 && octets[1] == 254) {
            return SourceUrlValidationResult.rejected("IPv4 host must not be link-local or metadata service");
        }

        return SourceUrlValidationResult.allow();
    }
}
