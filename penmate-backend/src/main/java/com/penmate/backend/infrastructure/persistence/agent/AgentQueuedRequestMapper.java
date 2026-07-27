package com.penmate.backend.infrastructure.persistence.agent;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

@Mapper
public interface AgentQueuedRequestMapper {
    @Select("""
            SELECT request_id AS "requestId", project_id AS "projectId", session_id AS "sessionId",
                   owner_user_id AS "ownerUserId", request_type AS "requestType",
                   payload_json::text AS "payloadJson", request_status AS "requestStatus",
                   attempt_count AS "attemptCount", last_error AS "lastError",
                   created_at AS "createdAt", updated_at AS "updatedAt"
            FROM agent_session_queued_requests
            WHERE project_id = #{projectId} AND session_id = #{sessionId}
              AND request_status IN ('PENDING', 'EXECUTING')
            LIMIT 1
            """)
    Map<String, Object> findOpen(@Param("projectId") Long projectId, @Param("sessionId") Long sessionId);

    @Insert("""
            INSERT INTO agent_session_queued_requests(
                request_id, project_id, session_id, owner_user_id, request_type, payload_json
            ) VALUES (
                #{requestId}, #{projectId}, #{sessionId}, #{ownerUserId}, #{requestType},
                CAST(#{payloadJson,jdbcType=VARCHAR} AS JSONB)
            )
            ON CONFLICT DO NOTHING
            """)
    int insert(@Param("requestId") Long requestId,
               @Param("projectId") Long projectId,
               @Param("sessionId") Long sessionId,
               @Param("ownerUserId") Long ownerUserId,
               @Param("requestType") String requestType,
               @Param("payloadJson") String payloadJson);

    @Update("""
            UPDATE agent_session_queued_requests
            SET request_status = 'WITHDRAWN', updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND session_id = #{sessionId}
              AND request_id = #{requestId} AND owner_user_id = #{ownerUserId}
              AND request_status = 'PENDING'
            """)
    int withdraw(@Param("projectId") Long projectId,
                 @Param("sessionId") Long sessionId,
                 @Param("requestId") Long requestId,
                 @Param("ownerUserId") Long ownerUserId);

    @Select(value = """
            WITH candidate AS (
                SELECT q.id
                FROM agent_session_queued_requests q
                WHERE q.request_status = 'PENDING'
                  AND NOT EXISTS (
                    SELECT 1 FROM agent_runs r
                    WHERE r.session_id = q.session_id
                      AND r.run_status IN ('PENDING', 'RUNNING', 'WAITING_APPROVAL', 'SUSPENDED')
                  )
                ORDER BY q.created_at, q.id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
            )
            UPDATE agent_session_queued_requests q
            SET request_status = 'EXECUTING', attempt_count = attempt_count + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            FROM candidate
            WHERE q.id = candidate.id
            RETURNING q.request_id AS "requestId", q.project_id AS "projectId",
                      q.session_id AS "sessionId", q.owner_user_id AS "ownerUserId",
                      q.request_type AS "requestType", q.payload_json::text AS "payloadJson",
                      q.request_status AS "requestStatus", q.attempt_count AS "attemptCount",
                      q.last_error AS "lastError", q.created_at AS "createdAt", q.updated_at AS "updatedAt"
            """, affectData = true)
    Map<String, Object> claimNextIdle();

    @Update("""
            UPDATE agent_session_queued_requests
            SET request_status = 'COMPLETED', last_error = NULL, updated_at = CURRENT_TIMESTAMP(3)
            WHERE request_id = #{requestId} AND request_status = 'EXECUTING'
            """)
    int markCompleted(@Param("requestId") Long requestId);

    @Update("""
            UPDATE agent_session_queued_requests
            SET request_status = 'PENDING', last_error = #{error}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE request_id = #{requestId} AND request_status = 'EXECUTING'
            """)
    int requeue(@Param("requestId") Long requestId, @Param("error") String error);

    @Update("""
            UPDATE agent_session_queued_requests
            SET request_status = 'FAILED', last_error = #{error}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE request_id = #{requestId} AND request_status = 'EXECUTING'
            """)
    int markFailed(@Param("requestId") Long requestId, @Param("error") String error);
}
