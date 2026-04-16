package com.penmate.backend.application.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务异常：用于应用层/领域层主动抛出可预期错误。
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final Object details;

    private BusinessException(HttpStatus httpStatus, String errorCode, String message, Object details) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.details = details;
    }

    public static BusinessException of(String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", message, null);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, null);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message, null);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", message, null);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message, null);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", message, null);
    }

    public static BusinessException of(HttpStatus status, String errorCode, String message, Object details) {
        return new BusinessException(status, errorCode, message, details);
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object getDetails() {
        return details;
    }
}

