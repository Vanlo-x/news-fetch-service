package com.vanlo.newsfetch.infrastructure;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;

class NoProxyAwareProxySelector extends ProxySelector {

    private final ProxySelector delegate;
    private final List<String> noProxyHosts;

    NoProxyAwareProxySelector(ProxySelector delegate, List<String> noProxyHosts) {
        this.delegate = delegate;
        this.noProxyHosts = noProxyHosts == null ? List.of() : List.copyOf(noProxyHosts);
    }

    @Override
    public List<Proxy> select(URI uri) {
        String host = uri.getHost();
        if (host != null && matchesNoProxy(host)) {
            return List.of(Proxy.NO_PROXY);
        }
        return delegate.select(uri);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress socketAddress, IOException exception) {
        delegate.connectFailed(uri, socketAddress, exception);
    }

    private boolean matchesNoProxy(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        for (String noProxyHost : noProxyHosts) {
            if ("*".equals(noProxyHost)
                    || normalizedHost.equals(noProxyHost)
                    || normalizedHost.endsWith("." + noProxyHost)) {
                return true;
            }
        }
        return false;
    }
}
