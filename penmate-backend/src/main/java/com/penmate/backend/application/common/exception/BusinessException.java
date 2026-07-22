package com.penmate.backend.application.common.exception;

/**
 * 业务异常：用于应用层/领域层主动抛出可预期错误。
 */
public class BusinessException extends RuntimeException {

    private final BusinessErrorType type;
    private final String errorCode;
    private final Object details;

    private BusinessException(BusinessErrorType type, String errorCode, String message, Object details) {
        super(message);
        this.type = type;
        this.errorCode = errorCode;
        this.details = details;
    }

    public static BusinessException of(String message) {
        return new BusinessException(BusinessErrorType.BUSINESS_RULE, "BUSINESS_RULE_VIOLATION", message, null);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(BusinessErrorType.INVALID_REQUEST, "BAD_REQUEST", message, null);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(BusinessErrorType.UNAUTHENTICATED, "UNAUTHORIZED", message, null);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(BusinessErrorType.FORBIDDEN, "FORBIDDEN", message, null);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(BusinessErrorType.NOT_FOUND, "RESOURCE_NOT_FOUND", message, null);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(BusinessErrorType.CONFLICT, "RESOURCE_CONFLICT", message, null);
    }

    public static BusinessException of(BusinessErrorType type, String errorCode, String message, Object details) {
        return new BusinessException(type, errorCode, message, details);
    }

    public BusinessErrorType getType() {
        return type;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object getDetails() {
        return details;
    }
}

