package com.penmate.backend.application.common.exception;

public enum BusinessErrorType {
    BUSINESS_RULE,
    INVALID_REQUEST,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE
}
