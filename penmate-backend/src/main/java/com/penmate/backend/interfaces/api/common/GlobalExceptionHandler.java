package com.penmate.backend.interfaces.api.common;

import com.penmate.backend.application.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        String traceId = traceId(request);
        int status = ex.getHttpStatus().value();
        log.warn("业务异常: traceId={}, path={}, errorCode={}, message={}",
                traceId,
                request.getRequestURI(),
                ex.getErrorCode(),
                ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(
                ErrorResponse.of(
                        status,
                        ex.getErrorCode(),
                        ex.getMessage(),
                        ex.getDetails(),
                        request.getRequestURI(),
                        traceId
                )
        );
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

    private String traceId(HttpServletRequest request) {
        Object traceIdAttr = request.getAttribute("traceId");
        if (traceIdAttr instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        String traceId = request.getHeader("X-Trace-Id");
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }
}

