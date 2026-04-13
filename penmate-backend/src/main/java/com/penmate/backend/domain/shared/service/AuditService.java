package com.penmate.backend.domain.shared.service;

public interface AuditService {

    void write(String traceId,
               Long userId,
               String module,
               String action,
               String resourceType,
               String resourceId,
               String requestJson,
               int responseCode);
}

