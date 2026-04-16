package com.penmate.backend.infrastructure.persistence.audit;

import com.penmate.backend.domain.shared.service.AuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * AuditServiceImpl。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Service
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public AuditServiceImpl(AuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理业务请求。
     *
     * @param traceId 入参：traceId
     * @param userId 入参：userId
     * @param module 入参：module
     * @param action 入参：action
     * @param resourceType 入参：resourceType
     * @param resourceId 入参：resourceId
     * @param requestJson 入参：requestJson
     * @param responseCode 入参：responseCode
     */
    @Override
    public void write(String traceId,
                      Long userId,
                      String module,
                      String action,
                      String resourceType,
                      String resourceId,
                      String requestJson,
                      int responseCode) {
        auditLogMapper.insert(traceId, userId, module, action, resourceType, resourceId, normalizeToJson(requestJson), responseCode);
    }

    private String normalizeToJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        try {
            objectMapper.readTree(raw);
            return raw;
        } catch (JsonProcessingException ignore) {
            try {
                return objectMapper.writeValueAsString(raw);
            } catch (JsonProcessingException e) {
                return "{}";
            }
        }
    }
}

