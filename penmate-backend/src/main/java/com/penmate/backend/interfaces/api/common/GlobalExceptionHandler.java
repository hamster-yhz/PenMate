package com.penmate.backend.interfaces.api.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> Map.of("field", err.getField(), "message", err.getDefaultMessage()))
                .toList();

        return ResponseEntity.badRequest().body(
                ErrorResponse.of(
                        400,
                        "VALIDATION_ERROR",
                        "Input validation failed.",
                        details,
                        traceId(request)
                )
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBiz(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ErrorResponse.of(
                        422,
                        "BUSINESS_RULE_VIOLATION",
                        ex.getMessage(),
                        null,
                        traceId(request)
                )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.of(
                        500,
                        "INTERNAL_SERVER_ERROR",
                        "Internal server error",
                        null,
                        traceId(request)
                )
        );
    }

    private String traceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }
}

