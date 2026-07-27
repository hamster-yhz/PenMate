package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface AgentSessionMapper {

    @Select("""
            SELECT id,
                   session_id AS conversation_id,
                   project_id,
                   owner_user_id AS user_id,
                   title,
                   NULL AS context_scope_json,
                   last_message_at,
                   session_status AS status,
                   created_at,
                   updated_at,
                   deleted_at
            FROM agent_sessions
            WHERE project_id = #{projectId}
              AND owner_user_id = #{userId}
              AND ((#{deleted} = TRUE AND deleted_at IS NOT NULL)
                   OR (#{deleted} = FALSE AND deleted_at IS NULL))
            ORDER BY COALESCE(last_message_at, created_at) DESC, id DESC
            """)
    List<AgentConversation> listConversationSummaries(@Param("projectId") Long projectId,
                                                      @Param("userId") Long userId,
                                                      @Param("deleted") boolean deleted);

    @Select("""
            SELECT id,
                   session_id AS conversation_id,
                   project_id,
                   owner_user_id AS user_id,
                   title,
                   NULL AS context_scope_json,
                   last_message_at,
                   session_status AS status,
                   created_at,
                   updated_at,
                   deleted_at
            FROM agent_sessions
            WHERE project_id = #{projectId} AND session_id = #{conversationId} AND deleted_at IS NULL
            LIMIT 1
            """)
    AgentConversation findConversationSummary(@Param("projectId") Long projectId,
                                              @Param("conversationId") Long conversationId);

    @Select("""
            SELECT id,
                   session_id AS conversation_id,
                   project_id,
                   owner_user_id AS user_id,
                   title,
                   NULL AS context_scope_json,
                   last_message_at,
                   session_status AS status,
                   created_at,
                   updated_at,
                   deleted_at
            FROM agent_sessions
            WHERE project_id = #{projectId} AND session_id = #{conversationId}
            LIMIT 1
            """)
    AgentConversation findConversationSummaryIncludingDeleted(@Param("projectId") Long projectId,
                                                               @Param("conversationId") Long conversationId);

    @Update("""
            UPDATE agent_sessions
            SET title = #{title}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND session_id = #{conversationId}
              AND owner_user_id = #{userId} AND deleted_at IS NULL
            """)
    int updateConversationTitle(@Param("projectId") Long projectId,
                                @Param("conversationId") Long conversationId,
                                @Param("userId") Long userId,
                                @Param("title") String title);

    @Update("""
            UPDATE agent_sessions
            SET deleted_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND session_id = #{conversationId}
              AND owner_user_id = #{userId} AND deleted_at IS NULL
            """)
    int softDeleteConversation(@Param("projectId") Long projectId,
                               @Param("conversationId") Long conversationId,
                               @Param("userId") Long userId);

    @Update("""
            UPDATE agent_sessions
            SET deleted_at = NULL, updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND session_id = #{conversationId}
              AND owner_user_id = #{userId} AND deleted_at IS NOT NULL
            """)
    int restoreConversation(@Param("projectId") Long projectId,
                            @Param("conversationId") Long conversationId,
                            @Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*)
            FROM agent_runs
            WHERE session_id = #{conversationId}
              AND run_status IN ('PENDING', 'RUNNING', 'WAITING_APPROVAL', 'SUSPENDED')
            """)
    int countActiveRuns(@Param("conversationId") Long conversationId);

    @Select("""
            SELECT run_status
            FROM agent_runs
            WHERE session_id = #{conversationId}
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    String findLatestRunStatus(@Param("conversationId") Long conversationId);

    @Insert("""
            INSERT INTO agent_sessions(
                session_id, project_id, owner_user_id, title, session_status,
                bound_style_id, active_context_epoch_id,
                last_turn_id, last_run_id, last_message_at, resumed_at
            ) VALUES (
                #{conversationId}, #{projectId}, #{userId}, #{title}, #{status},
                NULL, NULL, NULL, NULL, NULL, NULL
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertConversationSummary(AgentConversation conversation);

    @Select("""
            SELECT id,
                   session_id,
                   project_id,
                   owner_user_id,
                   title,
                   session_status,
                   bound_style_id,
                   active_context_epoch_id,
                   last_turn_id,
                   last_run_id,
                   resumed_at,
                   total_prompt_tokens,
                   total_completion_tokens,
                   total_tokens,
                   created_at,
                   updated_at
            FROM agent_sessions
            WHERE project_id = #{projectId} AND session_id = #{sessionId} AND deleted_at IS NULL
            LIMIT 1
            """)
    AgentSession findSession(@Param("projectId") Long projectId,
                             @Param("sessionId") Long sessionId);

    @Select("""
            SELECT id AS id,
                   session_id AS sessionId,
                   project_id AS projectId,
                   owner_user_id AS ownerUserId,
                   title AS title,
                   session_status AS sessionStatus,
                   bound_style_id AS boundStyleId,
                   active_context_epoch_id AS activeContextEpochId,
                   last_turn_id AS lastTurnId,
                   last_run_id AS lastRunId,
                   resumed_at AS resumedAt,
                   total_prompt_tokens AS totalPromptTokens,
                   total_completion_tokens AS totalCompletionTokens,
                   total_tokens AS totalTokens,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM agent_sessions
            WHERE project_id = #{projectId} AND session_id = #{sessionId} AND deleted_at IS NULL
            LIMIT 1
            """)
    Map<String, Object> findSessionRow(@Param("projectId") Long projectId,
                                       @Param("sessionId") Long sessionId);

    @Select("""
            SELECT s.session_id AS "sessionId",
                   COALESCE(OCTET_LENGTH(cs.summary_json::text), 0) + COALESCE((
                       SELECT SUM(OCTET_LENGTH(m.content_markdown))
                       FROM agent_messages m
                       WHERE m.session_id = s.session_id
                         AND m.seq_no > COALESCE(cs.cutoff_message_seq, 0)
                         AND m.role IN ('user', 'assistant')
                   ), 0) AS "contextUtf8Bytes",
                   model.model_config_id AS "modelConfigId",
                   model.model_name AS "modelName",
                   model.max_context_tokens AS "maxContextTokens",
                   model.max_output_tokens AS "maxOutputTokens",
                   model.context_capacity_source AS "contextCapacitySource",
                   latest_usage."modelConfigId" AS "latestUsageModelConfigId",
                   latest_usage."estimatedInputTokens" AS "latestInputTokens",
                   latest_usage."reservedOutputTokens" AS "latestReservedOutputTokens",
                   latest_usage."protectedTokens" AS "latestProtectedTokens",
                   latest_usage."usageSource" AS "latestUsageSource"
            FROM agent_sessions s
            LEFT JOIN agent_session_context_summaries cs ON cs.session_id = s.session_id
            LEFT JOIN model_user_preferences preferences ON preferences.user_id = s.owner_user_id
            LEFT JOIN LATERAL (
                SELECT mc.model_config_id, mc.model_name, mc.max_context_tokens, mc.max_output_tokens,
                       mc.context_capacity_source
                FROM model_configurations mc
                WHERE mc.model_config_id = COALESCE((
                    SELECT NULLIF(i.model_snapshot_json->>'modelConfigId', '')::BIGINT
                    FROM agent_runs r
                    JOIN agent_run_inputs i ON i.run_id = r.run_id
                    WHERE r.session_id = s.session_id
                      AND r.run_status IN ('PENDING', 'RUNNING', 'WAITING_APPROVAL', 'SUSPENDED')
                      AND NULLIF(i.model_snapshot_json->>'modelConfigId', '') IS NOT NULL
                    ORDER BY r.created_at DESC, r.id DESC
                    LIMIT 1
                ), preferences.default_creative_model_config_id)
                LIMIT 1
            ) model ON TRUE
            LEFT JOIN LATERAL (
                SELECT NULLIF(e.payload_json->>'modelConfigId', '')::BIGINT AS "modelConfigId",
                       NULLIF(e.payload_json->>'estimatedInputTokens', '')::INTEGER AS "estimatedInputTokens",
                       NULLIF(e.payload_json->>'reservedOutputTokens', '')::INTEGER AS "reservedOutputTokens",
                       NULLIF(e.payload_json->>'protectedTokens', '')::INTEGER AS "protectedTokens",
                       e.payload_json->>'usageSource' AS "usageSource"
                FROM agent_events e
                WHERE e.session_id = s.session_id AND e.event_type = 'context.usage.updated'
                ORDER BY e.created_at DESC, e.id DESC
                LIMIT 1
            ) latest_usage ON TRUE
            WHERE s.project_id = #{projectId} AND s.session_id = #{sessionId} AND s.deleted_at IS NULL
            LIMIT 1
            """)
    Map<String, Object> findSessionContextUsageRow(@Param("projectId") Long projectId,
                                                    @Param("sessionId") Long sessionId);

    @Select("""
            SELECT id
            FROM agent_sessions
            WHERE (#{projectId,jdbcType=BIGINT} IS NULL OR project_id = #{projectId,jdbcType=BIGINT})
              AND session_id = #{sessionId}
              AND deleted_at IS NULL
            LIMIT 1
            FOR UPDATE
            """)
    Long lockSessionForTurnAppend(@Param("projectId") Long projectId,
                                  @Param("sessionId") Long sessionId);

    @Select("""
            SELECT COALESCE(MAX(turn_seq), 0)
            FROM agent_turns
            WHERE session_id = #{sessionId}
            """)
    int maxTurnSeq(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT COALESCE(MAX(seq_no), 0)
            FROM agent_messages
            WHERE session_id = #{sessionId}
            """)
    int maxMessageSeq(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT assistant_message_id
            FROM agent_turns
            WHERE session_id = #{sessionId}
              AND turn_id = #{turnId}
            LIMIT 1
            """)
    Long findTurnAssistantMessageId(@Param("sessionId") Long sessionId,
                                    @Param("turnId") Long turnId);

    @Select("""
            SELECT turn_id AS turnId,
                   session_id AS sessionId,
                   turn_seq AS turnSeq,
                   user_message_id AS userMessageId,
                   assistant_message_id AS assistantMessageId,
                   run_id AS runId,
                   turn_status AS turnStatus,
                   resume_token AS resumeToken,
                   created_at AS createdAt
            FROM agent_turns
            WHERE session_id = #{sessionId}
            ORDER BY turn_seq ASC, id ASC
            """)
    List<Map<String, Object>> listTurnRows(@Param("sessionId") Long sessionId);

    @Insert("""
            INSERT INTO agent_turns(
                turn_id, session_id, turn_seq, user_message_id, assistant_message_id, run_id, turn_status, resume_token
            ) VALUES (
                #{turnId}, #{sessionId}, #{turnSeq}, #{userMessageId}, NULL, #{runId}, #{turnStatus}, #{resumeToken}
            )
            """)
    int insertTurn(@Param("sessionId") Long sessionId,
                   @Param("turnId") Long turnId,
                   @Param("turnSeq") Integer turnSeq,
                   @Param("userMessageId") Long userMessageId,
                   @Param("runId") Long runId,
                   @Param("turnStatus") String turnStatus,
                   @Param("resumeToken") String resumeToken);

    @Insert("""
            INSERT INTO agent_messages(
                message_id, session_id, turn_id, role, message_kind, content_markdown,
                render_blocks_json, tool_call_id, approval_id, delivery_status, seq_no
            ) VALUES (
                #{messageId}, #{sessionId}, #{turnId}, #{role}, #{messageKind}, #{contentMarkdown},
                NULL, NULL, NULL, 'FINAL', #{seqNo}
            )
            """)
    int insertSessionMessage(@Param("sessionId") Long sessionId,
                             @Param("turnId") Long turnId,
                             @Param("messageId") Long messageId,
                             @Param("role") String role,
                             @Param("messageKind") String messageKind,
                             @Param("contentMarkdown") String contentMarkdown,
                             @Param("seqNo") Integer seqNo);

    @Update("""
            UPDATE agent_turns
            SET assistant_message_id = #{assistantMessageId},
                turn_status = CASE
                    WHEN turn_status IN ('PENDING', 'RUNNING') THEN 'DONE'
                    ELSE turn_status
                END
            WHERE session_id = #{sessionId}
              AND turn_id = #{turnId}
            """)
    int updateTurnAssistantMessage(@Param("sessionId") Long sessionId,
                                   @Param("turnId") Long turnId,
                                   @Param("assistantMessageId") Long assistantMessageId);

    @Update("""
            UPDATE agent_messages
            SET content_markdown = #{contentMarkdown}
            WHERE session_id = #{sessionId}
              AND message_id = #{messageId}
              AND role = 'assistant'
            """)
    int updateMessageContent(@Param("sessionId") Long sessionId,
                             @Param("messageId") Long messageId,
                             @Param("contentMarkdown") String contentMarkdown);

    @Select("""
            SELECT model_name AS modelName,
                   max_context_tokens AS maxContextTokens
            FROM model_configurations
            WHERE model_config_id = #{modelConfigId}
              AND model_type = 'CHAT'
              AND status = 'ACTIVE'
              AND (scope_type = 'SYSTEM' OR (scope_type = 'USER' AND owner_user_id = #{userId}))
              AND deleted_at IS NULL
            LIMIT 1
            """)
    Map<String, Object> findModelConfigSummary(@Param("userId") Long userId,
                                               @Param("modelConfigId") Long modelConfigId);

    @Select("""
            SELECT m.message_id AS "messageId",
                   COALESCE(m.turn_id, t.turn_id) AS "turnId",
                   m.role AS role,
                   m.message_kind AS "messageKind",
                   m.content_markdown AS "contentMarkdown",
                   m.approval_id AS "approvalId",
                   m.seq_no AS "seqNo",
                   m.created_at AS "createdAt"
            FROM agent_messages m
            LEFT JOIN agent_turns t
              ON t.session_id = m.session_id
             AND (t.user_message_id = m.message_id OR t.assistant_message_id = m.message_id)
            WHERE m.session_id = #{sessionId}
            ORDER BY m.seq_no ASC, m.id ASC
            """)
    List<Map<String, Object>> listMessageRows(@Param("sessionId") Long sessionId);

    @Insert("""
            INSERT INTO agent_sessions(
                session_id, project_id, owner_user_id, title, session_status,
                bound_style_id, active_context_epoch_id,
                last_turn_id, last_run_id, last_message_at, resumed_at
            ) VALUES (
                #{sessionId}, #{projectId}, #{ownerUserId}, #{title}, #{sessionStatus},
                #{boundStyleId}, #{activeContextEpochId},
                #{lastTurnId}, #{lastRunId}, NULL, #{resumedAt}
            )
            """)
    int insertSession(AgentSession session);

    @Update("""
            UPDATE agent_sessions
            SET last_turn_id = #{turnId}
            WHERE project_id = #{projectId} AND session_id = #{sessionId} AND deleted_at IS NULL
            """)
    int updateLastTurn(@Param("projectId") Long projectId,
                       @Param("sessionId") Long sessionId,
                       @Param("turnId") Long turnId);

    @Update("""
            UPDATE agent_sessions
            SET last_run_id = #{runId}
            WHERE project_id = #{projectId} AND session_id = #{sessionId} AND deleted_at IS NULL
            """)
    int updateLastRun(@Param("projectId") Long projectId,
                      @Param("sessionId") Long sessionId,
                      @Param("runId") Long runId);

    @Update("""
            UPDATE agent_turns
            SET run_id = #{successorRunId}, turn_status = 'PENDING', updated_at = CURRENT_TIMESTAMP(3)
            WHERE session_id = #{sessionId} AND turn_id = #{turnId} AND run_id = #{expectedRunId}
            """)
    int rebindTurnRun(@Param("sessionId") Long sessionId,
                      @Param("turnId") Long turnId,
                      @Param("expectedRunId") Long expectedRunId,
                      @Param("successorRunId") Long successorRunId);

    @Update("""
            UPDATE agent_sessions
            SET bound_style_id = #{styleId}
            WHERE project_id = #{projectId} AND session_id = #{sessionId} AND deleted_at IS NULL
            """)
    int updateBoundStyle(@Param("projectId") Long projectId,
                         @Param("sessionId") Long sessionId,
                         @Param("styleId") Long styleId);

    @Update("""
            UPDATE agent_sessions
            SET total_prompt_tokens = total_prompt_tokens + #{promptTokens},
                total_completion_tokens = total_completion_tokens + #{completionTokens},
                total_tokens = total_tokens + #{totalTokens},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND session_id = #{sessionId} AND deleted_at IS NULL
            """)
    int incrementSessionTokenUsage(@Param("projectId") Long projectId,
                                   @Param("sessionId") Long sessionId,
                                   @Param("promptTokens") Integer promptTokens,
                                   @Param("completionTokens") Integer completionTokens,
                                   @Param("totalTokens") Integer totalTokens);

    @Insert("""
            INSERT INTO agent_session_style_bindings(binding_id, session_id, style_id, source)
            VALUES (#{bindingId}, #{sessionId}, #{styleId}, 'MANUAL_SWITCH')
            """)
    int insertStyleBinding(@Param("bindingId") Long bindingId,
                           @Param("sessionId") Long sessionId,
                           @Param("styleId") Long styleId);

    @Update("""
            UPDATE agent_session_style_bindings
            SET deactivated_at = CURRENT_TIMESTAMP(3)
            WHERE session_id = #{sessionId} AND deactivated_at IS NULL
            """)
    int deactivateStyleBindings(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT binding_id
            FROM agent_session_style_bindings
            WHERE session_id = #{sessionId} AND deactivated_at IS NULL
            ORDER BY activated_at DESC, id DESC
            LIMIT 1
            """)
    Long findActiveStyleBindingRevision(@Param("sessionId") Long sessionId);
}
