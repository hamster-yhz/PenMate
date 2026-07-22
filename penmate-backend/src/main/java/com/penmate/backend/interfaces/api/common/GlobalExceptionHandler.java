package com.penmate.backend.interfaces.api.common;

import com.penmate.backend.application.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String traceId = traceId(request);
        log.warn("请求参数校验失败: traceId={}, path={}, errorCount={}", traceId, request.getRequestURI(), ex.getBindingResult().getErrorCount());
        List<Map<String, String>> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> Map.of("field", err.getField(), "message", err.getDefaultMessage()))
                .toList();

        return ResponseEntity.badRequest().body(
                ErrorResponse.of(
                        400,
                        "VALIDATION_ERROR",
                        "请求参数校验失败",
                        details,
                        request.getRequestURI(),
                        traceId
                )
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameter(MissingServletRequestParameterException ex,
                                                                       HttpServletRequest request) {
        String traceId = traceId(request);
        log.warn("请求参数缺失: traceId={}, path={}, parameter={}", traceId, request.getRequestURI(), ex.getParameterName());
        List<Map<String, String>> details = List.of(
                Map.of(
                        "field", ex.getParameterName(),
                        "message", "required request parameter is missing"
                )
        );

        return ResponseEntity.badRequest().body(
                ErrorResponse.of(
                        400,
                        "VALIDATION_ERROR",
                        "请求参数校验失败",
                        details,
                        request.getRequestURI(),
                        traceId
                )
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpServletRequest request) {
        String traceId = traceId(request);
        log.warn("请求体不可读: traceId={}, path={}, message={}", traceId, request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        List<Map<String, String>> details = List.of(
                Map.of(
                        "field", "requestBody",
                        "message", ex.getMostSpecificCause().getMessage()
                )
        );

        return ResponseEntity.badRequest().body(
                ErrorResponse.of(
                        400,
                        "VALIDATION_ERROR",
                        "请求参数校验失败",
                        details,
                        request.getRequestURI(),
                        traceId
                )
        );
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        String traceId = traceId(request);
        HttpStatus status = mapStatus(ex);
        log.warn("业务异常: traceId={}, path={}, errorCode={}, message={}",
                traceId,
                request.getRequestURI(),
                ex.getErrorCode(),
                ex.getMessage());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if ("RATE_LIMIT_EXCEEDED".equals(ex.getErrorCode()) && ex.getDetails() instanceof Map<?, ?> details) {
            Object retryAfter = details.get("retryAfterSeconds");
            if (retryAfter != null) response.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        }
        return response.body(
                ErrorResponse.of(
                        status.value(),
                        ex.getErrorCode(),
                        ex.getMessage(),
                        ex.getDetails(),
                        request.getRequestURI(),
                        traceId
                )
        );
    }

    private HttpStatus mapStatus(BusinessException exception) {
        return switch (exception.getType()) {
            case BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        String traceId = traceId(request);
        log.warn("非法参数异常: traceId={}, path={}, message={}", traceId, request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ErrorResponse.of(
                        422,
                        "BUSINESS_RULE_VIOLATION",
                        ex.getMessage(),
                        null,
                        request.getRequestURI(),
                        traceId
                )
        );
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(NoHandlerFoundException ex, HttpServletRequest request) {
        return notFoundResponse(request, ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return notFoundResponse(request, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex, HttpServletRequest request) {
        String traceId = traceId(request);
        log.error("系统异常: traceId={}, path={}", traceId, request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.of(
                        500,
                        "INTERNAL_SERVER_ERROR",
                        "系统开小差了，请稍后重试",
                        null,
                        request.getRequestURI(),
                        traceId
                )
        );
    }

    private ResponseEntity<ErrorResponse> notFoundResponse(HttpServletRequest request, String message) {
        String traceId = traceId(request);
        log.warn("资源不存在: traceId={}, path={}, message={}", traceId, request.getRequestURI(), message);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse.of(
                        404,
                        "NOT_FOUND",
                        "请求的资源不存在",
                        null,
                        request.getRequestURI(),
                        traceId
                )
        );
    }

    private String traceId(HttpServletRequest request) {
        Object traceIdAttr = request.getAttribute("traceId");
        if (traceIdAttr instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return traceId.trim();
    }
}
