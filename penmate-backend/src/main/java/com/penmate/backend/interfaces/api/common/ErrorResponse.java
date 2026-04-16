package com.penmate.backend.interfaces.api.common;

import java.time.Instant;

public class ErrorResponse {

    private int status;
    private String errorCode;
    private String message;
    private Object details;
    private String path;
    private Meta meta;

    public static ErrorResponse of(int status,
                                   String errorCode,
                                   String message,
                                   Object details,
                                   String path,
                                   String traceId) {
        ErrorResponse response = new ErrorResponse();
        response.status = status;
        response.errorCode = errorCode;
        response.message = message;
        response.details = details;
        response.path = path;
        response.meta = new Meta(traceId, Instant.now().toString());
        return response;
    }

    public int getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public Object getDetails() {
        return details;
    }

    public String getPath() {
        return path;
    }

    public Meta getMeta() {
        return meta;
    }

    public static class Meta {
        private String traceId;
        private String timestamp;

        public Meta(String traceId, String timestamp) {
            this.traceId = traceId;
            this.timestamp = timestamp;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getTimestamp() {
            return timestamp;
        }
    }
}

