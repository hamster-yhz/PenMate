package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentCheckpointMapper {

    @Insert("""
            INSERT INTO agent_checkpoints(
                checkpoint_id, run_id, checkpoint_no, last_event_seq, state_json, state_size_bytes,
                state_schema_version, state_sha256, state_object_key, storage_tier,
                cold_archived_at, expires_at
            )
            VALUES(
                #{checkpointId}, #{runId}, #{checkpointNo}, #{lastEventSeq}, #{stateJson}, #{stateSizeBytes},
                #{stateSchemaVersion}, #{stateSha256}, #{stateObjectKey}, #{storageTier},
                #{coldArchivedAt}, #{expiresAt}
            )
            """)
    int insert(AgentCheckpoint checkpoint);

    @Select("""
            SELECT checkpoint_id, run_id, checkpoint_no, last_event_seq, state_json, state_size_bytes,
                   state_schema_version, state_sha256, state_object_key, storage_tier,
                   cold_archived_at, expires_at, created_at
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
            @Arg(column = "storage_tier", javaType = String.class),
            @Arg(column = "cold_archived_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "expires_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    AgentCheckpoint findLatest(@Param("runId") Long runId);

    @Select("""
            SELECT checkpoint_id, run_id, checkpoint_no, last_event_seq, state_json, state_size_bytes,
                   state_schema_version, state_sha256, state_object_key, storage_tier,
                   cold_archived_at, expires_at, created_at
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
            @Arg(column = "storage_tier", javaType = String.class),
            @Arg(column = "cold_archived_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "expires_at", javaType = java.time.LocalDateTime.class),
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

    @Select("""
            SELECT c.checkpoint_id, c.run_id, c.checkpoint_no, c.last_event_seq,
                   c.state_json, c.state_size_bytes, c.state_schema_version, c.state_sha256,
                   c.state_object_key, c.storage_tier, c.cold_archived_at, c.expires_at, c.created_at
            FROM agent_checkpoints c
            INNER JOIN agent_runs r ON r.run_id = c.run_id
            WHERE r.run_status IN ('DONE','FAILED','CANCELLED','SUPERSEDED')
              AND r.finished_at < #{cutoff}
              AND c.storage_tier = 'HOT'
            ORDER BY c.checkpoint_id
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
            @Arg(column = "storage_tier", javaType = String.class),
            @Arg(column = "cold_archived_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "expires_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    List<AgentCheckpoint> findTerminalHotBefore(@Param("cutoff") LocalDateTime cutoff,
                                                @Param("limit") int limit);

    @Update("""
            UPDATE agent_checkpoints
            SET state_json = #{stateJson}, state_object_key = #{stateObjectKey},
                state_sha256 = #{stateSha256}, storage_tier = 'COLD',
                cold_archived_at = #{archivedAt}, expires_at = #{expiresAt}
            WHERE checkpoint_id = #{checkpointId} AND storage_tier = 'HOT'
            """)
    int markCold(@Param("checkpointId") Long checkpointId,
                 @Param("stateJson") String stateJson,
                 @Param("stateObjectKey") String stateObjectKey,
                 @Param("stateSha256") String stateSha256,
                 @Param("archivedAt") LocalDateTime archivedAt,
                 @Param("expiresAt") LocalDateTime expiresAt);

    @Select("""
            SELECT checkpoint_id, run_id, checkpoint_no, last_event_seq, state_json, state_size_bytes,
                   state_schema_version, state_sha256, state_object_key, storage_tier,
                   cold_archived_at, expires_at, created_at
            FROM agent_checkpoints
            WHERE storage_tier = 'COLD' AND expires_at <= #{now}
            ORDER BY expires_at, checkpoint_id
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
            @Arg(column = "storage_tier", javaType = String.class),
            @Arg(column = "cold_archived_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "expires_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    List<AgentCheckpoint> findExpiredCold(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Delete("DELETE FROM agent_checkpoints WHERE checkpoint_id = #{checkpointId} AND storage_tier = 'COLD'")
    int deleteCold(@Param("checkpointId") Long checkpointId);
}
