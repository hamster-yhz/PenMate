package com.penmate.backend.interfaces.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void should_trim_trace_id_header_before_setting_request_attribute_and_response_header() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "  trace-123  ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(request.getAttribute(TraceIdFilter.TRACE_ID_KEY)).isEqualTo("trace-123");
        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("trace-123");
    }
}

