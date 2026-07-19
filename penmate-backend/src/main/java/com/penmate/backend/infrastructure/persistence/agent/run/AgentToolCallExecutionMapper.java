package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentToolCallExecution;
import org.apache.ibatis.annotations.*;

import java.time.Instant;

@Mapper
public interface AgentToolCallExecutionMapper {

    @Select("""
            SELECT execution_id, run_id, tool_call_id, tool_code, request_sha256,
                   execution_token, execution_status, result_json, error_code, error_message,
                   started_at, finished_at
            FROM agent_tool_call_executions
            WHERE run_id = #{runId} AND tool_call_id = #{toolCallId}
            """)
    @ConstructorArgs({
            @Arg(column = "execution_id", javaType = Long.class),
            @Arg(column = "run_id", javaType = Long.class),
            @Arg(column = "tool_call_id", javaType = String.class),
            @Arg(column = "tool_code", javaType = String.class),
            @Arg(column = "request_sha256", javaType = String.class),
            @Arg(column = "execution_token", javaType = Long.class),
            @Arg(column = "execution_status", javaType = String.class),
            @Arg(column = "result_json", javaType = String.class),
            @Arg(column = "error_code", javaType = String.class),
            @Arg(column = "error_message", javaType = String.class),
            @Arg(column = "started_at", javaType = Instant.class),
            @Arg(column = "finished_at", javaType = Instant.class)
    })
    AgentToolCallExecution find(@Param("runId") Long runId, @Param("toolCallId") String toolCallId);

    @Insert("""
            INSERT INTO agent_tool_call_executions(
                execution_id, run_id, tool_call_id, tool_code, request_sha256,
                execution_token, execution_status
            ) VALUES (
                #{executionId}, #{runId}, #{toolCallId}, #{toolCode}, #{requestSha256},
                #{executionToken}, 'STARTED'
            )
            """)
    int insertStarted(AgentToolCallExecution execution);

    @Update("""
            UPDATE agent_tool_call_executions
            SET execution_status = #{status}, result_json = #{resultJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler},
                error_code = #{errorCode}, error_message = #{errorMessage},
                finished_at = #{finishedAt}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE execution_id = #{executionId}
              AND execution_token = #{executionToken}
              AND execution_status = 'STARTED'
            """)
    int markFinished(@Param("executionId") Long executionId,
                     @Param("executionToken") Long executionToken,
                     @Param("status") String status,
                     @Param("resultJson") String resultJson,
                     @Param("errorCode") String errorCode,
                     @Param("errorMessage") String errorMessage,
                     @Param("finishedAt") Instant finishedAt);
}
