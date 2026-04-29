package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PendingToolInvocationMapper {

    @Insert("""
            INSERT INTO pending_tool_invocations(
                approval_id, project_id, task_id, conversation_id, tool_code,
                tool_args_json, context_json, operator_id, trace_id, idempotency_key, status
            ) VALUES (
                #{approvalId}, #{projectId}, #{taskId}, #{conversationId}, #{toolCode},
                #{toolArgsJson}, #{contextJson}, #{operatorId}, #{traceId}, #{idempotencyKey}, #{status}
            )
            """)
    int insert(PendingToolInvocationSnapshot snapshot);

    @Select("""
            SELECT approval_id, project_id, task_id, conversation_id, tool_code,
                   CAST(tool_args_json AS CHAR) AS tool_args_json,
                   CAST(context_json AS CHAR) AS context_json,
                   operator_id, trace_id, idempotency_key, status
            FROM pending_tool_invocations
            WHERE approval_id = #{approvalId}
            LIMIT 1
            """)
    PendingToolInvocationSnapshot findByApprovalId(@Param("approvalId") Long approvalId);

    @Update("""
            UPDATE pending_tool_invocations
            SET status = #{targetStatus},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE approval_id = #{approvalId}
              AND status = #{expectedStatus}
            """)
    int markStatus(@Param("approvalId") Long approvalId,
                   @Param("expectedStatus") String expectedStatus,
                   @Param("targetStatus") String targetStatus);
}
