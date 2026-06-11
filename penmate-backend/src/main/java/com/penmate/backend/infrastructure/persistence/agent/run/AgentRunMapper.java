package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentRunMapper {

    @Insert("""
            INSERT INTO agent_runs(
                run_id, project_id, session_id, turn_id, owner_user_id,
                run_status, run_phase, active_approval_id, latest_event_seq,
                latest_checkpoint_id, trace_id, started_at, finished_at
            )
            VALUES(
                #{runId}, #{projectId}, #{sessionId}, #{turnId}, #{ownerUserId},
                #{runStatus}, #{runPhase}, #{activeApprovalId}, #{latestEventSeq},
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
                #{styleSnapshotJson}, #{modelSnapshotJson}, #{pluginBindingsJson}, #{inputHash}
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
                run_status AS runStatus,
                run_phase AS runPhase,
                active_approval_id AS activeApprovalId,
                latest_event_seq AS latestEventSeq,
                latest_checkpoint_id AS latestCheckpointId,
                trace_id AS traceId,
                started_at AS startedAt,
                finished_at AS finishedAt
            FROM agent_runs
            WHERE run_id = #{runId}
            """)
    AgentRun findRun(Long runId);
}
