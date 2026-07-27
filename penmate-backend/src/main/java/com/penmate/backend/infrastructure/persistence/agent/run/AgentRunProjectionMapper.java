package com.penmate.backend.infrastructure.persistence.agent.run;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

@Mapper
public interface AgentRunProjectionMapper {

    @Select("""
            SELECT latest_sequence
            FROM agent_run_projections
            WHERE run_id = #{runId}
            LIMIT 1
            """)
    Long findLatestSequence(@Param("runId") Long runId);

    @Select("""
            SELECT run_id AS "runId",
                   session_id AS "sessionId",
                   turn_id AS "turnId",
                   run_status AS "runStatus",
                   run_phase AS "runPhase",
                   latest_sequence AS "latestSequence",
                   active_approval_id AS "activeApprovalId"
            FROM agent_run_projections
            WHERE project_id = #{projectId}
              AND session_id = #{sessionId}
            ORDER BY updated_at DESC, id DESC
            LIMIT 1
            """)
    Map<String, Object> findLatestRunForSession(@Param("projectId") Long projectId,
                                                @Param("sessionId") Long sessionId);

    @Update("""
            UPDATE agent_run_projections
            SET run_status = COALESCE(#{status}, run_status),
                run_phase = COALESCE(#{phase}, run_phase),
                active_approval_id = COALESCE(#{activeApprovalId}, active_approval_id),
                last_error_code = #{errorCode},
                last_error_message = #{errorMessage},
                latest_sequence = #{sequence},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE run_id = #{runId}
              AND latest_sequence < #{sequence}
            """)
    int updateRunState(@Param("runId") Long runId,
                       @Param("status") String status,
                       @Param("phase") String phase,
                       @Param("activeApprovalId") Long activeApprovalId,
                       @Param("sequence") Long sequence,
                       @Param("errorCode") String errorCode,
                       @Param("errorMessage") String errorMessage);

    @Insert("""
            INSERT INTO agent_run_projections(
                run_id, project_id, session_id, turn_id, run_status, run_phase, latest_sequence
            )
            SELECT r.run_id, r.project_id, r.session_id, r.turn_id,
                   COALESCE(#{status}, r.run_status),
                   COALESCE(#{phase}, r.run_phase),
                   #{sequence}
            FROM agent_runs r
            WHERE r.run_id = #{runId}
            ON CONFLICT (run_id) DO UPDATE SET
                run_status = COALESCE(#{status}, agent_run_projections.run_status),
                run_phase = COALESCE(#{phase}, agent_run_projections.run_phase),
                active_approval_id = COALESCE(#{activeApprovalId}, agent_run_projections.active_approval_id),
                last_error_code = #{errorCode},
                last_error_message = #{errorMessage},
                latest_sequence = GREATEST(agent_run_projections.latest_sequence, #{sequence}),
                updated_at = CURRENT_TIMESTAMP(3)
            """)
    int upsertRunState(@Param("runId") Long runId,
                       @Param("status") String status,
                       @Param("phase") String phase,
                       @Param("activeApprovalId") Long activeApprovalId,
                       @Param("sequence") Long sequence,
                       @Param("errorCode") String errorCode,
                       @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE agent_run_projections
            SET status_message = CONCAT(COALESCE(status_message, ''), #{text}),
                latest_sequence = #{sequence},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE run_id = #{runId}
              AND latest_sequence < #{sequence}
            """)
    int appendAssistantDelta(@Param("runId") Long runId,
                             @Param("sequence") Long sequence,
                             @Param("text") String text);

    @Update("""
            UPDATE agent_run_projections
            SET current_assistant_message_id = #{messageId},
                latest_sequence = #{sequence},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE run_id = #{runId}
              AND latest_sequence < #{sequence}
            """)
    int setCurrentAssistantMessage(@Param("runId") Long runId,
                                   @Param("messageId") Long messageId,
                                   @Param("sequence") Long sequence);

    @Insert("""
            INSERT INTO agent_tool_call_projections(
                run_id, tool_call_id, tool_code, tool_name, status, iteration,
                arguments_preview_json, output_preview, output_artifact_id,
                approval_id, error_code, error_message
            )
            VALUES(
                #{runId}, #{toolCallId}, #{toolCode}, #{toolName}, #{status}, #{iteration},
                #{argumentsPreviewJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler}, #{outputPreview}, #{outputArtifactId},
                #{approvalId}, #{errorCode}, #{errorMessage}
            )
            ON CONFLICT (run_id, tool_call_id) DO UPDATE SET
                tool_code = EXCLUDED.tool_code,
                tool_name = COALESCE(EXCLUDED.tool_name, agent_tool_call_projections.tool_name),
                status = EXCLUDED.status,
                iteration = COALESCE(EXCLUDED.iteration, agent_tool_call_projections.iteration),
                arguments_preview_json = COALESCE(EXCLUDED.arguments_preview_json, agent_tool_call_projections.arguments_preview_json),
                output_preview = EXCLUDED.output_preview,
                output_artifact_id = EXCLUDED.output_artifact_id,
                approval_id = EXCLUDED.approval_id,
                error_code = EXCLUDED.error_code,
                error_message = EXCLUDED.error_message,
                updated_at = CURRENT_TIMESTAMP(3)
            """)
    int upsertToolCall(@Param("runId") Long runId,
                       @Param("toolCallId") String toolCallId,
                       @Param("toolCode") String toolCode,
                       @Param("toolName") String toolName,
                       @Param("status") String status,
                       @Param("iteration") Integer iteration,
                       @Param("argumentsPreviewJson") String argumentsPreviewJson,
                       @Param("outputPreview") String outputPreview,
                       @Param("outputArtifactId") Long outputArtifactId,
                       @Param("approvalId") Long approvalId,
                       @Param("errorCode") String errorCode,
                       @Param("errorMessage") String errorMessage);

    @Insert("""
            INSERT INTO agent_todo_projections(
                run_id, todo_id, title, status, sort_order,
                blocked_reason, error_summary, completed_summary
            )
            VALUES(
                #{runId}, #{todoId}, #{title}, #{status}, #{sortOrder},
                #{blockedReason}, #{errorSummary}, #{completedSummary}
            )
            ON CONFLICT (run_id, todo_id) DO UPDATE SET
                title = EXCLUDED.title,
                status = EXCLUDED.status,
                sort_order = EXCLUDED.sort_order,
                blocked_reason = EXCLUDED.blocked_reason,
                error_summary = EXCLUDED.error_summary,
                completed_summary = EXCLUDED.completed_summary,
                updated_at = CURRENT_TIMESTAMP(3)
            """)
    int upsertTodo(@Param("runId") Long runId,
                   @Param("todoId") String todoId,
                   @Param("title") String title,
                   @Param("status") String status,
                   @Param("sortOrder") Integer sortOrder,
                   @Param("blockedReason") String blockedReason,
                   @Param("errorSummary") String errorSummary,
                   @Param("completedSummary") String completedSummary);

    @Update("""
            UPDATE agent_todo_projections
            SET status = 'DELETE',
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE run_id = #{runId}
              AND todo_id = #{todoId}
            """)
    int deleteTodo(@Param("runId") Long runId,
                   @Param("todoId") String todoId);
}
