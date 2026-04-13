package com.penmate.backend.infrastructure.persistence.audit;

import com.penmate.backend.domain.shared.service.AuditService;
import org.springframework.stereotype.Service;

@Service
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper auditLogMapper;

    public AuditServiceImpl(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

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

