package com.penmate.backend.interfaces.api.common;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientIpResolverTest {

    @Test
    void ignores_proxy_header_by_default() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Real-IP")).thenReturn("203.0.113.5");

        assertThat(new ClientIpResolver(false).resolve(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void trusts_only_single_nginx_overwritten_real_ip_when_enabled() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Real-IP")).thenReturn("203.0.113.5");
        assertThat(new ClientIpResolver(true).resolve(request)).isEqualTo("203.0.113.5");

        when(request.getHeader("X-Real-IP")).thenReturn("203.0.113.5, 10.0.0.1");
        assertThat(new ClientIpResolver(true).resolve(request)).isEqualTo("unknown");
    }
}
