package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentCheckpointMapper {

    @Insert("""
            INSERT INTO agent_checkpoints(
                checkpoint_id, run_id, checkpoint_no, last_event_seq, state_json, state_size_bytes
            )
            VALUES(
                #{checkpointId}, #{runId}, #{checkpointNo}, #{lastEventSeq}, #{stateJson}, #{stateSizeBytes}
            )
            """)
    int insert(AgentCheckpoint checkpoint);

    @Select("""
            SELECT checkpoint_id, run_id, checkpoint_no, last_event_seq, state_json, state_size_bytes, created_at
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
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    AgentCheckpoint findLatest(@Param("runId") Long runId);
}
