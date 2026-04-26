package com.penmate.backend.interfaces.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    @Test
    void should_reject_null_trace_id_filter_in_constructor() {
        assertThatThrownBy(() -> new SecurityConfig(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("traceIdFilter");
    }

    @Test
    void should_expose_trace_id_header_in_cors_configuration() {
        SecurityConfig securityConfig = new SecurityConfig(new TraceIdFilter());

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(configuration).isNotNull();
        assertThat(configuration.getExposedHeaders())
                .contains("Authorization", TraceIdFilter.TRACE_ID_HEADER);
    }

    @Test
    void should_reject_null_http_security_in_filter_chain() {
        SecurityConfig securityConfig = new SecurityConfig(new TraceIdFilter());

        assertThatThrownBy(() -> securityConfig.securityFilterChain((HttpSecurity) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("http")
                .hasMessageContaining("must not be null");
    }
}

