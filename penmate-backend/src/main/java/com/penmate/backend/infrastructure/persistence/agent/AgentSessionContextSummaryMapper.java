package com.penmate.backend.infrastructure.persistence.agent;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface AgentSessionContextSummaryMapper {
    @Select("""
            SELECT session_id AS "sessionId", project_id AS "projectId", owner_user_id AS "ownerUserId",
                   summary_json::text AS "summaryJson", cutoff_message_seq AS "cutoffMessageSeq",
                   prompt_tokens AS "promptTokens", completion_tokens AS "completionTokens",
                   updated_at AS "updatedAt"
            FROM agent_session_context_summaries
            WHERE session_id = #{sessionId}
            LIMIT 1
            """)
    Map<String, Object> find(@Param("sessionId") Long sessionId);

    @Insert("""
            INSERT INTO agent_session_context_summaries(
                session_id, project_id, owner_user_id, summary_json, cutoff_message_seq,
                prompt_tokens, completion_tokens
            ) VALUES (
                #{sessionId}, #{projectId}, #{ownerUserId}, CAST(#{summaryJson} AS JSONB),
                #{cutoffMessageSeq}, #{promptTokens}, #{completionTokens}
            )
            ON CONFLICT (session_id) DO UPDATE SET
                summary_json = EXCLUDED.summary_json,
                cutoff_message_seq = EXCLUDED.cutoff_message_seq,
                prompt_tokens = EXCLUDED.prompt_tokens,
                completion_tokens = EXCLUDED.completion_tokens,
                updated_at = CURRENT_TIMESTAMP(3)
            """)
    int upsert(@Param("sessionId") Long sessionId,
               @Param("projectId") Long projectId,
               @Param("ownerUserId") Long ownerUserId,
               @Param("summaryJson") String summaryJson,
               @Param("cutoffMessageSeq") Integer cutoffMessageSeq,
               @Param("promptTokens") Integer promptTokens,
               @Param("completionTokens") Integer completionTokens);
}
