package com.penmate.backend.infrastructure.security;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.model.service.ModelEndpointPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DefaultModelEndpointPolicy implements ModelEndpointPolicy {
    private final boolean allowPrivate;
    private final Set<String> adminPrivateHostAllowlist;

    public DefaultModelEndpointPolicy(
            @Value("${penmate.security.model-endpoint.allow-private:false}") boolean allowPrivate,
            @Value("${penmate.security.model-endpoint.admin-private-host-allowlist:}") String allowlist) {
        this.allowPrivate = allowPrivate;
        this.adminPrivateHostAllowlist = Arrays.stream(allowlist.split(","))
                .map(String::strip).map(value -> value.toLowerCase(Locale.ROOT)).filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String validate(String baseUrl, boolean systemScope) {
        URI uri;
        try {
            uri = URI.create(baseUrl).normalize();
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("Model Base URL is invalid");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isBlank() || uri.getUserInfo() != null || uri.getFragment() != null
                || !("https".equals(scheme) || allowPrivate && "http".equals(scheme))) {
            throw BusinessException.badRequest("Model Base URL must use HTTPS and contain no credentials or fragment");
        }
        boolean explicitlyAllowed = allowPrivate || systemScope && adminPrivateHostAllowlist.contains(host);
        for (InetAddress address : resolve(host)) {
            if (isBlocked(address) && !explicitlyAllowed) {
                throw BusinessException.badRequest("Model Base URL resolves to a private or reserved address");
            }
        }
        String normalized = uri.toString();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private InetAddress[] resolve(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) throw new UnknownHostException(host);
            return addresses;
        } catch (UnknownHostException exception) {
            throw BusinessException.badRequest("Model Base URL host cannot be resolved");
        }
    }

    private boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first == 0 || first == 10 || first == 127 || first == 224 || first >= 240
                    || first == 169 && second == 254 || first == 172 && second >= 16 && second <= 31
                    || first == 192 && second == 168 || first == 100 && second >= 64 && second <= 127;
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            return (first & 0xfe) == 0xfc || first == 0xff;
        }
        return true;
    }
}
