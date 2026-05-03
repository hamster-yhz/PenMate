package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PendingToolInvocationMapper {

    @Insert("""
            INSERT INTO pending_tool_invocations(
                approval_id, project_id, task_id, conversation_id, tool_code,
                tool_args_json, context_json, operator_id, trace_id, idempotency_key, status,
                loop_run_id, llm_turn_index, tool_call_id, assistant_tool_calls_json,
                conversation_messages_json, resume_mode, approval_summary_json
            ) VALUES (
                #{approvalId}, #{projectId}, #{taskId}, #{conversationId}, #{toolCode},
                #{toolArgsJson}, #{contextJson}, #{operatorId}, #{traceId}, #{idempotencyKey}, #{status},
                #{loopRunId}, #{llmTurnIndex}, #{toolCallId}, #{assistantToolCallsJson},
                #{conversationMessagesJson}, #{resumeMode}, #{approvalSummaryJson}
            )
            """)
    int insert(PendingToolInvocationSnapshot snapshot);

    @Select("""
            SELECT approval_id, project_id, task_id, conversation_id, tool_code,
                   CAST(tool_args_json AS CHAR(4000)) AS tool_args_json,
                   CAST(context_json AS CHAR(4000)) AS context_json,
                   operator_id, trace_id, idempotency_key, status,
                   loop_run_id, llm_turn_index, tool_call_id,
                   CAST(assistant_tool_calls_json AS CHAR(4000)) AS assistant_tool_calls_json,
                   CAST(conversation_messages_json AS CHAR(4000)) AS conversation_messages_json,
                   resume_mode,
                   CAST(approval_summary_json AS CHAR(4000)) AS approval_summary_json
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

    @Select("""
            SELECT approval_id, project_id, task_id, conversation_id, tool_code,
                   CAST(tool_args_json AS CHAR(4000)) AS tool_args_json,
                   CAST(context_json AS CHAR(4000)) AS context_json,
                   operator_id, trace_id, idempotency_key, status,
                   loop_run_id, llm_turn_index, tool_call_id,
                   CAST(assistant_tool_calls_json AS CHAR(4000)) AS assistant_tool_calls_json,
                   CAST(conversation_messages_json AS CHAR(4000)) AS conversation_messages_json,
                   resume_mode,
                   CAST(approval_summary_json AS CHAR(4000)) AS approval_summary_json
            FROM pending_tool_invocations
            WHERE status = 'executing'
              AND updated_at < TIMESTAMPADD(MINUTE, -#{timeoutMinutes}, CURRENT_TIMESTAMP(3))
            ORDER BY updated_at ASC
            LIMIT #{limit}
            """)
    List<PendingToolInvocationSnapshot> findStaleExecutingSnapshots(@Param("timeoutMinutes") int timeoutMinutes,
                                                                    @Param("limit") int limit);
}
