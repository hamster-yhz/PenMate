package com.penmate.backend.infrastructure.persistence.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditLogMapper {

    @Insert("""
            INSERT INTO ops_audit_logs
            (trace_id, user_id, module, action, resource_type, resource_id, request_json, response_code)
            VALUES
            (#{traceId}, #{userId}, #{module}, #{action}, #{resourceType}, #{resourceId}, #{requestJson}, #{responseCode})
            """)
    int insert(
            @Param("traceId") String traceId,
            @Param("userId") Long userId,
            @Param("module") String module,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("requestJson") String requestJson,
            @Param("responseCode") int responseCode
    );
}

