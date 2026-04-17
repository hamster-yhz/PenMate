package com.penmate.backend.interfaces.api.common;

import java.time.Instant;

public class ErrorResponse {

    private ErrorData data;
    private Meta meta;

    public static ErrorResponse of(int status,
                                   String errorCode,
                                   String message,
                                   Object details,
                                   String path,
                                   String traceId) {
        ErrorResponse response = new ErrorResponse();
        response.data = new ErrorData(status, errorCode, message, details, path);
        response.meta = new Meta(traceId, Instant.now().toString());
        return response;
    }

    public ErrorData getData() {
        return data;
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

    public static class ErrorData {
        private final int status;
        private final String errorCode;
        private final String message;
        private final Object details;
        private final String path;

        public ErrorData(int status, String errorCode, String message, Object details, String path) {
            this.status = status;
            this.errorCode = errorCode;
            this.message = message;
            this.details = details;
            this.path = path;
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
    }
}
