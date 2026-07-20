package com.penmate.backend.interfaces.api.common;

import com.penmate.backend.application.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void should_return_400_with_validation_details_when_method_argument_not_valid() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        when(request.getRequestURI()).thenReturn("/api/v1/models");
        when(request.getAttribute("traceId")).thenReturn("trace-from-attr");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getErrorCount()).thenReturn(1);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("req", "name", "must not be blank")));

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        ErrorResponse body = response.getBody();
        assertThat(body.getData().getStatus()).isEqualTo(400);
        assertThat(body.getData().getErrorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.getData().getMessage()).isEqualTo("请求参数校验失败");
        assertThat(body.getData().getPath()).isEqualTo("/api/v1/models");
        assertThat(body.getMeta().getTraceId()).isEqualTo("trace-from-attr");
        assertThat(body.getData().getDetails()).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> details = (List<Map<String, String>>) body.getData().getDetails();
        assertThat(details).containsExactly(Map.of("field", "name", "message", "must not be blank"));
    }

    @Test
    void should_return_400_when_missing_request_parameter() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("projectId", "Long");

        when(request.getRequestURI()).thenReturn("/api/v1/novels");
        when(request.getAttribute("traceId")).thenReturn(null);
        when(request.getHeader("X-Trace-Id")).thenReturn("trace-from-header");

        ResponseEntity<ErrorResponse> response = handler.handleMissingRequestParameter(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        ErrorResponse body = response.getBody();
        assertThat(body.getData().getStatus()).isEqualTo(400);
        assertThat(body.getData().getErrorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.getMeta().getTraceId()).isEqualTo("trace-from-header");
        assertThat(body.getData().getDetails()).isEqualTo(List.of(Map.of("field", "projectId", "message", "required request parameter is missing")));
    }

    @Test
    void should_return_business_exception_status_and_payload() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        BusinessException ex = BusinessException.of(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "版本冲突", Map.of("version", "12"));

        when(request.getRequestURI()).thenReturn("/api/v1/novels/1/chapters/2");
        when(request.getAttribute("traceId")).thenReturn("trace-biz");

        ResponseEntity<ErrorResponse> response = handler.handleBusiness(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        ErrorResponse body = response.getBody();
        assertThat(body.getData().getStatus()).isEqualTo(409);
        assertThat(body.getData().getErrorCode()).isEqualTo("RESOURCE_CONFLICT");
        assertThat(body.getData().getMessage()).isEqualTo("版本冲突");
        assertThat(body.getData().getDetails()).isEqualTo(Map.of("version", "12"));
        assertThat(body.getMeta().getTraceId()).isEqualTo("trace-biz");
    }

    @Test
    void should_expose_retry_after_for_rate_limit_error() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getAttribute("traceId")).thenReturn("trace-rate-limit");
        BusinessException exception = BusinessException.of(HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT_EXCEEDED", "Too many requests", Map.of("retryAfterSeconds", 42L));

        ResponseEntity<ErrorResponse> response = handler.handleBusiness(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("42");
    }

    @Test
    void should_return_422_when_illegal_argument_exception() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        IllegalArgumentException ex = new IllegalArgumentException("非法参数");

        when(request.getRequestURI()).thenReturn("/api/v1/plugins/install");
        when(request.getAttribute("traceId")).thenReturn("");
        when(request.getHeader("X-Trace-Id")).thenReturn("header-illegal");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        ErrorResponse body = response.getBody();
        assertThat(body.getData().getStatus()).isEqualTo(422);
        assertThat(body.getData().getErrorCode()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(body.getData().getMessage()).isEqualTo("非法参数");
        assertThat(body.getMeta().getTraceId()).isEqualTo("header-illegal");
    }

    @Test
    void should_return_500_and_generate_trace_id_when_unknown_exception_without_trace_header() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        RuntimeException ex = new RuntimeException("boom");

        when(request.getRequestURI()).thenReturn("/api/v1/ops/status");
        when(request.getAttribute("traceId")).thenReturn(null);
        when(request.getHeader("X-Trace-Id")).thenReturn("   ");

        ResponseEntity<ErrorResponse> response = handler.handleUnknown(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        ErrorResponse body = response.getBody();
        assertThat(body.getData().getStatus()).isEqualTo(500);
        assertThat(body.getData().getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(body.getData().getMessage()).isEqualTo("系统开小差了，请稍后重试");
        assertThat(body.getData().getPath()).isEqualTo("/api/v1/ops/status");
        assertThat(body.getMeta().getTraceId()).isNotBlank();
    }

    @Test
    void should_trim_trace_id_from_header_before_putting_into_response_meta() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        IllegalArgumentException ex = new IllegalArgumentException("illegal");

        when(request.getRequestURI()).thenReturn("/api/v1/model/keys");
        when(request.getAttribute("traceId")).thenReturn(null);
        when(request.getHeader("X-Trace-Id")).thenReturn("  trace-from-header  ");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(ex, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMeta().getTraceId()).isEqualTo("trace-from-header");
    }

    @Test
    void should_return_500_when_null_pointer_exception() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        NullPointerException ex = new NullPointerException("projectId must not be null");

        when(request.getRequestURI()).thenReturn("/api/v1/novels/projects/1/chapters/2/versions/3");
        when(request.getAttribute("traceId")).thenReturn("trace-npe");

        ResponseEntity<ErrorResponse> response = handler.handleUnknown(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        ErrorResponse body = response.getBody();
        assertThat(body.getData().getStatus()).isEqualTo(500);
        assertThat(body.getData().getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(body.getData().getMessage()).isEqualTo("系统开小差了，请稍后重试");
        assertThat(body.getMeta().getTraceId()).isEqualTo("trace-npe");
    }
}

