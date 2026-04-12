package com.penmate.backend.interfaces.api.common;

import java.time.Instant;

public class ApiResponse<T> {

    private T data;
    private Meta meta;

    public ApiResponse() {
    }

    public ApiResponse(T data, String traceId) {
        this.data = data;
        this.meta = new Meta(traceId, Instant.now().toString());
    }

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(data, traceId);
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    public static class Meta {
        private String traceId;
        private String timestamp;

        public Meta() {
        }

        public Meta(String traceId, String timestamp) {
            this.traceId = traceId;
            this.timestamp = timestamp;
        }

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }
}

