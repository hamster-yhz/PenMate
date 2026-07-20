package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Mapper
public interface AgentRunMapper {

    @Insert("""
            INSERT INTO agent_runs(
                run_id, project_id, session_id, turn_id, owner_user_id, predecessor_run_id,
                run_status, run_phase, context_epoch_id, active_approval_id, latest_event_seq,
                latest_checkpoint_id, trace_id, started_at, finished_at
            )
            VALUES(
                #{runId}, #{projectId}, #{sessionId}, #{turnId}, #{ownerUserId}, #{predecessorRunId},
                #{runStatus}, #{runPhase}, #{contextEpochId}, #{activeApprovalId}, #{latestEventSeq},
                #{latestCheckpointId}, #{traceId}, #{startedAt}, #{finishedAt}
            )
            """)
    int insert(AgentRun run);

    @Insert("""
            INSERT INTO agent_run_inputs(
                run_id, prompt_snapshot, task_type, chapter_id, selected_text,
                style_snapshot_json, model_snapshot_json, plugin_bindings_json, input_hash
            )
            VALUES(
                #{runId}, #{promptSnapshot}, #{taskType}, #{chapterId}, #{selectedText},
                #{styleSnapshotJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler}, #{modelSnapshotJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler}, #{pluginBindingsJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler}, #{inputHash}
            )
            """)
    int insertInput(AgentRunInput input);

    @Select("""
            SELECT
                run_id AS runId,
                prompt_snapshot AS promptSnapshot,
                task_type AS taskType,
                chapter_id AS chapterId,
                selected_text AS selectedText,
                style_snapshot_json AS styleSnapshotJson,
                model_snapshot_json AS modelSnapshotJson,
                plugin_bindings_json AS pluginBindingsJson,
                input_hash AS inputHash
            FROM agent_run_inputs
            WHERE run_id = #{runId}
            """)
    AgentRunInput findInput(Long runId);

    @Select("""
            SELECT
                run_id AS runId,
                project_id AS projectId,
                session_id AS sessionId,
                turn_id AS turnId,
                owner_user_id AS ownerUserId,
                predecessor_run_id AS predecessorRunId,
                run_status AS runStatus,
                run_phase AS runPhase,
                context_epoch_id AS contextEpochId,
                active_approval_id AS activeApprovalId,
                lease_owner AS leaseOwner,
                lease_until AS leaseUntil,
                execution_token AS executionToken,
                attempt_count AS attemptCount,
                next_retry_at AS nextRetryAt,
                last_error_code AS lastErrorCode,
                last_error_message AS lastErrorMessage,
                latest_event_seq AS latestEventSeq,
                latest_checkpoint_id AS latestCheckpointId,
                trace_id AS traceId,
                started_at AS startedAt,
                finished_at AS finishedAt
            FROM agent_runs
            WHERE run_id = #{runId}
            """)
    AgentRun findRun(Long runId);

    @Select("""
            SELECT
                run_id AS runId,
                project_id AS projectId,
                session_id AS sessionId,
                turn_id AS turnId,
                owner_user_id AS ownerUserId,
                predecessor_run_id AS predecessorRunId,
                run_status AS runStatus,
                run_phase AS runPhase,
                context_epoch_id AS contextEpochId,
                active_approval_id AS activeApprovalId,
                lease_owner AS leaseOwner,
                lease_until AS leaseUntil,
                execution_token AS executionToken,
                attempt_count AS attemptCount,
                next_retry_at AS nextRetryAt,
                last_error_code AS lastErrorCode,
                last_error_message AS lastErrorMessage,
                latest_event_seq AS latestEventSeq,
                latest_checkpoint_id AS latestCheckpointId,
                trace_id AS traceId,
                started_at AS startedAt,
                finished_at AS finishedAt
            FROM agent_runs
            WHERE run_id = #{runId}
            FOR UPDATE
            """)
    AgentRun findRunForUpdate(Long runId);

    @Select("""
            SELECT
                run_id AS runId,
                project_id AS projectId,
                session_id AS sessionId,
                turn_id AS turnId,
                owner_user_id AS ownerUserId,
                predecessor_run_id AS predecessorRunId,
                run_status AS runStatus,
                run_phase AS runPhase,
                context_epoch_id AS contextEpochId,
                active_approval_id AS activeApprovalId,
                lease_owner AS leaseOwner,
                lease_until AS leaseUntil,
                execution_token AS executionToken,
                attempt_count AS attemptCount,
                next_retry_at AS nextRetryAt,
                last_error_code AS lastErrorCode,
                last_error_message AS lastErrorMessage,
                latest_event_seq AS latestEventSeq,
                latest_checkpoint_id AS latestCheckpointId,
                trace_id AS traceId,
                started_at AS startedAt,
                finished_at AS finishedAt
            FROM agent_runs
            WHERE predecessor_run_id = #{predecessorRunId}
            LIMIT 1
            """)
    AgentRun findSuccessor(Long predecessorRunId);

    @Update("""
            UPDATE agent_runs
            SET run_status = 'RUNNING',
                run_phase = CASE WHEN run_phase = 'created' THEN 'routing' ELSE run_phase END,
                lease_owner = #{owner},
                lease_until = #{leaseUntil},
                execution_token = execution_token + 1,
                attempt_count = attempt_count + 1,
                next_retry_at = NULL,
                last_error_code = NULL,
                last_error_message = NULL,
                started_at = COALESCE(started_at, #{now}),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE run_id = #{runId}
              AND (
                run_status = 'PENDING'
                OR (run_status = 'SUSPENDED' AND (next_retry_at IS NULL OR next_retry_at <= #{now}))
                OR (run_status = 'WAITING_APPROVAL' AND EXISTS (
                    SELECT 1 FROM agent_run_pending_approvals p
                    WHERE p.run_id = agent_runs.run_id AND p.pending_status = 'APPROVED'
                ))
              )
              AND (lease_until IS NULL OR lease_until < #{now})
            """)
    int acquireLease(@Param("runId") Long runId,
                     @Param("owner") String owner,
                     @Param("now") Instant now,
                     @Param("leaseUntil") Instant leaseUntil);

    @Select("""
            SELECT run_id AS runId, lease_owner AS owner, execution_token AS executionToken,
                   attempt_count AS attemptCount, lease_until AS expiresAt
            FROM agent_runs
            WHERE run_id = #{runId}
            """)
    Map<String, Object> findLease(@Param("runId") Long runId);

    @Update("""
            UPDATE agent_runs
            SET lease_until = #{leaseUntil}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE run_id = #{runId} AND run_status = 'RUNNING'
              AND lease_owner = #{owner} AND execution_token = #{executionToken}
            """)
    int renewLease(@Param("runId") Long runId,
                   @Param("owner") String owner,
                   @Param("executionToken") Long executionToken,
                   @Param("leaseUntil") Instant leaseUntil);

    @Select("""
            SELECT COUNT(*)
            FROM agent_runs
            WHERE run_id = #{runId} AND run_status = 'RUNNING'
              AND lease_owner = #{owner} AND execution_token = #{executionToken}
              AND lease_until >= #{now}
            """)
    int ownsLease(@Param("runId") Long runId,
                  @Param("owner") String owner,
                  @Param("executionToken") Long executionToken,
                  @Param("now") Instant now);

    @Select("""
            SELECT COUNT(*)
            FROM agent_runs
            WHERE run_id = #{runId} AND run_status = 'RUNNING'
              AND execution_token = #{executionToken}
              AND lease_until >= #{now}
            """)
    int ownsExecutionToken(@Param("runId") Long runId,
                           @Param("executionToken") Long executionToken,
                           @Param("now") Instant now);

    @Update("""
            UPDATE agent_runs
            SET run_status = #{targetStatus},
                run_phase = COALESCE(#{phase}, run_phase),
                active_approval_id = #{activeApprovalId},
                lease_owner = NULL,
                lease_until = NULL,
                next_retry_at = #{nextRetryAt},
                last_error_code = #{errorCode},
                last_error_message = #{errorMessage},
                finished_at = CASE WHEN #{terminal} = TRUE THEN CURRENT_TIMESTAMP(3) ELSE NULL END,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE run_id = #{runId} AND run_status = 'RUNNING'
              AND lease_owner = #{owner} AND execution_token = #{executionToken}
            """)
    int transitionWithLease(@Param("runId") Long runId,
                            @Param("owner") String owner,
                            @Param("executionToken") Long executionToken,
                            @Param("targetStatus") String targetStatus,
                            @Param("phase") String phase,
                            @Param("activeApprovalId") Long activeApprovalId,
                            @Param("nextRetryAt") Instant nextRetryAt,
                            @Param("errorCode") String errorCode,
                            @Param("errorMessage") String errorMessage,
                            @Param("terminal") boolean terminal);

    @Update("""
            UPDATE agent_runs
            SET run_status = #{targetStatus}, run_phase = #{phase},
                active_approval_id = NULL, lease_owner = NULL, lease_until = NULL,
                next_retry_at = NULL, last_error_code = #{errorCode},
                last_error_message = #{errorMessage}, finished_at = CURRENT_TIMESTAMP(3),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE run_id = #{runId} AND run_status = #{expectedStatus}
            """)
    int transitionExpected(@Param("runId") Long runId,
                           @Param("expectedStatus") String expectedStatus,
                           @Param("targetStatus") String targetStatus,
                           @Param("phase") String phase,
                           @Param("errorCode") String errorCode,
                           @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE agent_runs
            SET run_status = 'CANCELLED', run_phase = 'cancelled',
                active_approval_id = NULL, lease_owner = NULL, lease_until = NULL,
                next_retry_at = NULL, last_error_code = #{errorCode},
                last_error_message = #{errorMessage}, finished_at = CURRENT_TIMESTAMP(3),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE run_id = #{runId}
              AND run_status IN ('PENDING','RUNNING','WAITING_APPROVAL','SUSPENDED')
            """)
    int cancelRecoverable(@Param("runId") Long runId,
                          @Param("errorCode") String errorCode,
                          @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE agent_runs
            SET run_status = CASE WHEN attempt_count >= #{maxAttempts} THEN 'FAILED' ELSE 'SUSPENDED' END,
                run_phase = CASE WHEN attempt_count >= #{maxAttempts} THEN 'failed' ELSE 'suspended' END,
                lease_owner = NULL,
                lease_until = NULL,
                next_retry_at = CASE WHEN attempt_count >= #{maxAttempts}
                    THEN NULL ELSE CAST(#{nextRetryAt} AS TIMESTAMPTZ) END,
                last_error_code = 'AGENT_RUN_LEASE_EXPIRED',
                last_error_message = 'Agent worker lease expired before completion',
                finished_at = CASE WHEN attempt_count >= #{maxAttempts} THEN CURRENT_TIMESTAMP(3) ELSE NULL END,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE run_status = 'RUNNING' AND lease_until < #{now}
            """)
    int suspendExpiredRuns(@Param("now") Instant now,
                           @Param("nextRetryAt") Instant nextRetryAt,
                           @Param("maxAttempts") int maxAttempts);

    @Select("""
            SELECT r.run_id
            FROM agent_runs r
            WHERE (
                r.run_status = 'PENDING'
                OR (r.run_status = 'SUSPENDED' AND (r.next_retry_at IS NULL OR r.next_retry_at <= #{now}))
                OR (r.run_status = 'WAITING_APPROVAL' AND EXISTS (
                    SELECT 1 FROM agent_run_pending_approvals p
                    WHERE p.run_id = r.run_id AND p.pending_status = 'APPROVED'
                ))
            )
              AND (r.lease_until IS NULL OR r.lease_until < #{now})
            ORDER BY r.updated_at ASC
            LIMIT #{limit}
            """)
    List<Long> findClaimableRunIds(@Param("now") Instant now, @Param("limit") int limit);
}
