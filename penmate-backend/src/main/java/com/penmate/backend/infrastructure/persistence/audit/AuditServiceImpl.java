package com.penmate.backend.infrastructure.persistence.audit;

import com.penmate.backend.domain.shared.service.AuditService;
import org.springframework.stereotype.Service;

/**
 * AuditServiceImpl。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Service
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper auditLogMapper;

    public AuditServiceImpl(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
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
        auditLogMapper.insert(traceId, userId, module, action, resourceType, resourceId, requestJson, responseCode);
    }
}

