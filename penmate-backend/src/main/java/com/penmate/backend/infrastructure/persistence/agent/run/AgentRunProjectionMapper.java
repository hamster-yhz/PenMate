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
            SELECT run_id AS runId,
                   session_id AS sessionId,
                   turn_id AS turnId,
                   run_status AS runStatus,
                   run_phase AS runPhase,
                   latest_sequence AS latestSequence,
                   active_approval_id AS activeApprovalId
            FROM agent_run_projections
            WHERE project_id = #{projectId}
              AND session_id = #{sessionId}
            ORDER BY latest_sequence DESC, id DESC
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
            SELECT run_id, project_id, session_id, turn_id,
                   COALESCE(#{status}, run_status),
                   COALESCE(#{phase}, run_phase),
                   #{sequence}
            FROM agent_runs
            WHERE run_id = #{runId}
            ON DUPLICATE KEY UPDATE
                run_status = COALESCE(#{status}, run_status),
                run_phase = COALESCE(#{phase}, run_phase),
                active_approval_id = COALESCE(#{activeApprovalId}, active_approval_id),
                last_error_code = #{errorCode},
                last_error_message = #{errorMessage},
                latest_sequence = GREATEST(latest_sequence, #{sequence}),
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
                #{argumentsPreviewJson}, #{outputPreview}, #{outputArtifactId},
                #{approvalId}, #{errorCode}, #{errorMessage}
            )
            ON DUPLICATE KEY UPDATE
                tool_code = VALUES(tool_code),
                tool_name = VALUES(tool_name),
                status = VALUES(status),
                iteration = VALUES(iteration),
                arguments_preview_json = VALUES(arguments_preview_json),
                output_preview = VALUES(output_preview),
                output_artifact_id = VALUES(output_artifact_id),
                approval_id = VALUES(approval_id),
                error_code = VALUES(error_code),
                error_message = VALUES(error_message),
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
}
