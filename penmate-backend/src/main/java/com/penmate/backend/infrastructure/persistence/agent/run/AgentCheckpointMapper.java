package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentCheckpointMapper {

    @Insert("""
            INSERT INTO agent_checkpoints(
                checkpoint_id, run_id, checkpoint_no, last_event_seq, state_json, state_size_bytes,
                state_schema_version, state_sha256, state_object_key
            )
            VALUES(
                #{checkpointId}, #{runId}, #{checkpointNo}, #{lastEventSeq}, #{stateJson}, #{stateSizeBytes},
                #{stateSchemaVersion}, #{stateSha256}, #{stateObjectKey}
            )
            """)
    int insert(AgentCheckpoint checkpoint);

    @Select("""
            SELECT checkpoint_id, run_id, checkpoint_no, last_event_seq, state_json, state_size_bytes,
                   state_schema_version, state_sha256, state_object_key, created_at
            FROM agent_checkpoints
            WHERE run_id = #{runId}
            ORDER BY checkpoint_no DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "checkpoint_id", javaType = Long.class),
            @Arg(column = "run_id", javaType = Long.class),
            @Arg(column = "checkpoint_no", javaType = Long.class),
            @Arg(column = "last_event_seq", javaType = Long.class),
            @Arg(column = "state_json", javaType = String.class),
            @Arg(column = "state_size_bytes", javaType = Integer.class),
            @Arg(column = "state_schema_version", javaType = Integer.class),
            @Arg(column = "state_sha256", javaType = String.class),
            @Arg(column = "state_object_key", javaType = String.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    AgentCheckpoint findLatest(@Param("runId") Long runId);

    @Select("""
            SELECT checkpoint_id, run_id, checkpoint_no, last_event_seq, state_json, state_size_bytes,
                   state_schema_version, state_sha256, state_object_key, created_at
            FROM agent_checkpoints
            WHERE run_id = #{runId}
            ORDER BY checkpoint_no DESC
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "checkpoint_id", javaType = Long.class),
            @Arg(column = "run_id", javaType = Long.class),
            @Arg(column = "checkpoint_no", javaType = Long.class),
            @Arg(column = "last_event_seq", javaType = Long.class),
            @Arg(column = "state_json", javaType = String.class),
            @Arg(column = "state_size_bytes", javaType = Integer.class),
            @Arg(column = "state_schema_version", javaType = Integer.class),
            @Arg(column = "state_sha256", javaType = String.class),
            @Arg(column = "state_object_key", javaType = String.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    List<AgentCheckpoint> findLatestLimit(@Param("runId") Long runId, @Param("limit") int limit);

    @Delete("""
            DELETE FROM agent_checkpoints
            WHERE run_id = #{runId}
              AND checkpoint_no <= (
                  SELECT threshold.checkpoint_no FROM (
                      SELECT checkpoint_no FROM agent_checkpoints
                      WHERE run_id = #{runId}
                      ORDER BY checkpoint_no DESC LIMIT #{keep}, 1
                  ) threshold
              )
            """)
    int deleteOlderThanLatest(@Param("runId") Long runId, @Param("keep") int keep);

    @Delete("""
            DELETE c FROM agent_checkpoints c
            INNER JOIN agent_runs r ON r.run_id = c.run_id
            WHERE r.run_status IN ('DONE','FAILED','CANCELLED','SUPERSEDED')
              AND r.finished_at < #{cutoff}
            """)
    int deleteTerminalOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
