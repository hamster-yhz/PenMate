package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentArtifact;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AgentArtifactMapper {

    @Insert("""
            INSERT INTO agent_artifacts(artifact_id, run_id, event_id, artifact_type, payload_json, size_bytes)
            VALUES(#{artifactId}, #{runId}, #{eventId}, #{artifactType}, #{payloadJson}, #{sizeBytes})
            """)
    int insert(AgentArtifact artifact);

    @Select("""
            SELECT artifact_id, run_id, event_id, artifact_type, payload_json, size_bytes, created_at
            FROM agent_artifacts
            WHERE artifact_id = #{artifactId}
            """)
    @ConstructorArgs({
            @Arg(column = "artifact_id", javaType = Long.class),
            @Arg(column = "run_id", javaType = Long.class),
            @Arg(column = "event_id", javaType = Long.class),
            @Arg(column = "artifact_type", javaType = String.class),
            @Arg(column = "payload_json", javaType = String.class),
            @Arg(column = "size_bytes", javaType = Integer.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    AgentArtifact findById(@Param("artifactId") Long artifactId);

    @Select("""
            SELECT artifact_id, run_id, event_id, artifact_type, payload_json, size_bytes, created_at
            FROM agent_artifacts
            WHERE run_id = #{runId} AND artifact_type = #{artifactType}
            ORDER BY created_at DESC, artifact_id DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "artifact_id", javaType = Long.class),
            @Arg(column = "run_id", javaType = Long.class),
            @Arg(column = "event_id", javaType = Long.class),
            @Arg(column = "artifact_type", javaType = String.class),
            @Arg(column = "payload_json", javaType = String.class),
            @Arg(column = "size_bytes", javaType = Integer.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    AgentArtifact findLatest(@Param("runId") Long runId, @Param("artifactType") String artifactType);
}
