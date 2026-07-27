package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AgentRunPendingApprovalMapper {

    @Insert("""
            INSERT INTO agent_run_pending_approvals(
                pending_approval_id, approval_id, run_id, project_id, session_id, turn_id,
                tool_call_id, tool_code, tool_args_json, tool_context_json, resume_payload_json,
                idempotency_key, pending_status, operator_id, trace_id, approval_binding_json
            )
            VALUES(
                #{pendingApprovalId}, #{approvalId}, #{runId}, #{projectId}, #{sessionId}, #{turnId},
                #{toolCallId}, #{toolCode}, #{toolArgsJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler}, #{toolContextJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler}, #{resumePayloadJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler},
                #{idempotencyKey}, #{pendingStatus}, #{operatorId}, #{traceId},
                #{approvalBindingJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler}
            )
            """)
    int insert(AgentRunPendingApproval pendingApproval);

    @Select("""
            SELECT
                id,
                pending_approval_id AS pendingApprovalId,
                approval_id AS approvalId,
                run_id AS runId,
                project_id AS projectId,
                session_id AS sessionId,
                turn_id AS turnId,
                tool_call_id AS toolCallId,
                tool_code AS toolCode,
                tool_args_json AS toolArgsJson,
                tool_context_json AS toolContextJson,
                resume_payload_json AS resumePayloadJson,
                idempotency_key AS idempotencyKey,
                pending_status AS pendingStatus,
                operator_id AS operatorId,
                trace_id AS traceId,
                created_at AS createdAt,
                updated_at AS updatedAt,
                approval_binding_json AS approvalBindingJson
            FROM agent_run_pending_approvals
            WHERE approval_id = #{approvalId}
            """)
    AgentRunPendingApproval findByApprovalId(@Param("approvalId") Long approvalId);

    @Select("""
            SELECT id, pending_approval_id AS pendingApprovalId, approval_id AS approvalId, run_id AS runId,
                   project_id AS projectId, session_id AS sessionId, turn_id AS turnId,
                   tool_call_id AS toolCallId, tool_code AS toolCode, tool_args_json AS toolArgsJson,
                   tool_context_json AS toolContextJson, resume_payload_json AS resumePayloadJson,
                   idempotency_key AS idempotencyKey, pending_status AS pendingStatus, operator_id AS operatorId,
                   trace_id AS traceId, created_at AS createdAt, updated_at AS updatedAt,
                   approval_binding_json AS approvalBindingJson
            FROM agent_run_pending_approvals
            WHERE idempotency_key = #{idempotencyKey}
            """)
    AgentRunPendingApproval findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT id, pending_approval_id AS pendingApprovalId, approval_id AS approvalId, run_id AS runId,
                   project_id AS projectId, session_id AS sessionId, turn_id AS turnId,
                   tool_call_id AS toolCallId, tool_code AS toolCode, tool_args_json AS toolArgsJson,
                   tool_context_json AS toolContextJson, resume_payload_json AS resumePayloadJson,
                   idempotency_key AS idempotencyKey, pending_status AS pendingStatus, operator_id AS operatorId,
                   trace_id AS traceId, created_at AS createdAt, updated_at AS updatedAt,
                   approval_binding_json AS approvalBindingJson
            FROM agent_run_pending_approvals
            WHERE run_id = #{runId} AND pending_status = 'APPROVED'
            ORDER BY updated_at DESC, id DESC LIMIT 1
            """)
    AgentRunPendingApproval findApprovedByRunId(Long runId);

    @Select("""
            SELECT id, pending_approval_id AS pendingApprovalId, approval_id AS approvalId, run_id AS runId,
                   project_id AS projectId, session_id AS sessionId, turn_id AS turnId,
                   tool_call_id AS toolCallId, tool_code AS toolCode, tool_args_json AS toolArgsJson,
                   tool_context_json AS toolContextJson, resume_payload_json AS resumePayloadJson,
                   idempotency_key AS idempotencyKey, pending_status AS pendingStatus, operator_id AS operatorId,
                   trace_id AS traceId, created_at AS createdAt, updated_at AS updatedAt,
                   approval_binding_json AS approvalBindingJson
            FROM agent_run_pending_approvals
            WHERE run_id = #{runId} AND pending_status = 'REJECTED'
            ORDER BY updated_at DESC, id DESC LIMIT 1
            """)
    AgentRunPendingApproval findRejectedByRunId(Long runId);

    @Select("""
            SELECT id, pending_approval_id AS pendingApprovalId, approval_id AS approvalId, run_id AS runId,
                   project_id AS projectId, session_id AS sessionId, turn_id AS turnId,
                   tool_call_id AS toolCallId, tool_code AS toolCode, tool_args_json AS toolArgsJson,
                   tool_context_json AS toolContextJson, resume_payload_json AS resumePayloadJson,
                   idempotency_key AS idempotencyKey, pending_status AS pendingStatus, operator_id AS operatorId,
                   trace_id AS traceId, created_at AS createdAt, updated_at AS updatedAt,
                   approval_binding_json AS approvalBindingJson
            FROM agent_run_pending_approvals
            WHERE run_id = #{runId} AND pending_status = 'PENDING'
            ORDER BY updated_at DESC, id DESC LIMIT 1
            """)
    AgentRunPendingApproval findPendingByRunId(Long runId);

    @Update("""
            UPDATE agent_run_pending_approvals
            SET pending_status = #{targetStatus},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE approval_id = #{approvalId}
              AND pending_status = #{expectedStatus}
            """)
    int markStatus(@Param("approvalId") Long approvalId,
                   @Param("expectedStatus") String expectedStatus,
                   @Param("targetStatus") String targetStatus);

    @Update("""
            UPDATE agent_run_pending_approvals
            SET pending_status = #{targetStatus},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE run_id = #{runId}
              AND tool_call_id = #{toolCallId}
              AND pending_status = #{expectedStatus}
            """)
    int markStatusByRunAndToolCall(@Param("runId") Long runId,
                                   @Param("toolCallId") String toolCallId,
                                   @Param("expectedStatus") String expectedStatus,
                                   @Param("targetStatus") String targetStatus);

    @Select("""
            SELECT
                id,
                pending_approval_id AS pendingApprovalId,
                approval_id AS approvalId,
                run_id AS runId,
                project_id AS projectId,
                session_id AS sessionId,
                turn_id AS turnId,
                tool_call_id AS toolCallId,
                tool_code AS toolCode,
                tool_args_json AS toolArgsJson,
                tool_context_json AS toolContextJson,
                resume_payload_json AS resumePayloadJson,
                idempotency_key AS idempotencyKey,
                pending_status AS pendingStatus,
                operator_id AS operatorId,
                trace_id AS traceId,
                created_at AS createdAt,
                updated_at AS updatedAt,
                approval_binding_json AS approvalBindingJson
            FROM agent_run_pending_approvals
            WHERE pending_status IN ('RESUMING', 'APPROVED')
              AND updated_at < CURRENT_TIMESTAMP(3) - (#{timeoutMinutes} * INTERVAL '1 minute')
            ORDER BY updated_at ASC
            LIMIT #{limit}
            """)
    List<AgentRunPendingApproval> findStaleResumingOrApproved(@Param("timeoutMinutes") int timeoutMinutes,
                                                              @Param("limit") int limit);

    @Update("""
            UPDATE agent_run_pending_approvals
            SET pending_status = 'INVALIDATED', updated_at = CURRENT_TIMESTAMP(3)
            WHERE run_id = #{runId} AND pending_status IN ('PENDING', 'APPROVED', 'RESUMING')
            """)
    int invalidateOpenByRunId(@Param("runId") Long runId);
}
