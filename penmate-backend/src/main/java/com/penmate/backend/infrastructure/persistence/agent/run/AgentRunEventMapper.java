package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentEvent;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AgentRunEventMapper {

    @Select("""
            SELECT latest_event_seq
            FROM agent_runs
            WHERE run_id = #{runId}
            FOR UPDATE
            """)
    Long lockLatestSequence(@Param("runId") Long runId);

    @Select("""
            SELECT run_id, project_id, session_id, turn_id
            FROM agent_runs
            WHERE run_id = #{runId}
            """)
    Map<String, Object> findRunIdentity(@Param("runId") Long runId);

    @Insert("""
            INSERT INTO agent_events(
                event_id, run_id, project_id, session_id, turn_id,
                sequence, schema_version, event_type, payload_json
            )
            VALUES(
                #{eventId}, #{runId}, #{projectId}, #{sessionId}, #{turnId},
                #{sequence}, #{schemaVersion}, #{eventType}, #{payloadJson}
            )
            """)
    int insert(AgentEvent event);

    @Update("""
            UPDATE agent_runs
            SET latest_event_seq = #{sequence},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE run_id = #{runId}
            """)
    int updateLatestSequence(@Param("runId") Long runId, @Param("sequence") Long sequence);

    @Select("""
            SELECT event_id, run_id, project_id, session_id, turn_id,
                   sequence, schema_version, event_type, payload_json, created_at
            FROM agent_events
            WHERE run_id = #{runId}
              AND sequence > #{after}
            ORDER BY sequence
            """)
    @ConstructorArgs({
            @Arg(column = "event_id", javaType = Long.class),
            @Arg(column = "run_id", javaType = Long.class),
            @Arg(column = "project_id", javaType = Long.class),
            @Arg(column = "session_id", javaType = Long.class),
            @Arg(column = "turn_id", javaType = Long.class),
            @Arg(column = "sequence", javaType = Long.class),
            @Arg(column = "schema_version", javaType = Integer.class),
            @Arg(column = "event_type", javaType = String.class),
            @Arg(column = "payload_json", javaType = String.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    List<AgentEvent> listAfter(@Param("runId") Long runId, @Param("after") Long after);

    @Select("""
            SELECT DISTINCT r.run_id
            FROM agent_runs r
            INNER JOIN agent_events e ON e.run_id = r.run_id
            WHERE r.run_status IN ('DONE','FAILED','CANCELLED','SUPERSEDED')
              AND r.finished_at < #{cutoff}
            ORDER BY r.run_id
            LIMIT #{limit}
            """)
    List<Long> findTerminalRunIdsWithEventsBefore(@Param("cutoff") LocalDateTime cutoff,
                                                   @Param("limit") int limit);

    @Delete("DELETE FROM agent_events WHERE run_id = #{runId} AND sequence <= #{maxSequence}")
    int deleteThrough(@Param("runId") Long runId, @Param("maxSequence") Long maxSequence);
}
