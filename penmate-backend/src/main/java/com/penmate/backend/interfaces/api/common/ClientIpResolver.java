package com.penmate.backend.interfaces.api.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class ClientIpResolver {
    private final boolean trustProxyHeaders;

    public ClientIpResolver(@Value("${penmate.rate-limit.trust-proxy-headers:false}") boolean trustProxyHeaders) {
        this.trustProxyHeaders = trustProxyHeaders;
    }

    public String resolve(HttpServletRequest request) {
        String candidate = trustProxyHeaders ? request.getHeader("X-Real-IP") : request.getRemoteAddr();
        if (candidate == null || candidate.isBlank() || candidate.contains(",")
                || !candidate.trim().matches("^[0-9a-fA-F:.]+$")) {
            return "unknown";
        }
        try {
            return InetAddress.getByName(candidate.trim()).getHostAddress();
        } catch (UnknownHostException exception) {
            return "unknown";
        }
    }
}
