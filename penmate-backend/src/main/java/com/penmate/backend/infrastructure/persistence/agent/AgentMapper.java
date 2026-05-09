package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * AgentMapper。
 * <p>基建层：负责 Agent 旧仓储接口到当前 session-centric schema 的兼容映射。</p>
 */
@Mapper
public interface AgentMapper {

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
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            ORDER BY COALESCE(last_message_at, created_at) DESC, id DESC
            """)
    List<AgentConversation> listConversations(@Param("projectId") Long projectId);

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
    AgentConversation findConversation(@Param("projectId") Long projectId,
                                       @Param("conversationId") Long conversationId);

    @Insert("""
            INSERT INTO agent_sessions(
                session_id, project_id, owner_user_id, title, session_status,
                bound_style_id, active_context_version, last_turn_id, last_task_id, last_message_at, resumed_at
            ) VALUES (
                #{conversationId}, #{projectId}, #{userId}, #{title}, #{status},
                NULL, 1, NULL, NULL, NULL, NULL
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertConversation(AgentConversation conversation);

    @Select("""
            SELECT id,
                   message_id,
                   session_id AS conversation_id,
                   role,
                   message_kind AS user_message_type,
                   content_markdown AS content_md,
                   CAST(render_blocks_json AS CHAR) AS attachments_json,
                   NULL AS tool_calls_json,
                   seq_no,
                   created_at
            FROM agent_messages
            WHERE session_id = #{conversationId}
            ORDER BY seq_no ASC, id ASC
            """)
    List<AgentMessage> listMessages(@Param("conversationId") Long conversationId);

    @Select("""
            SELECT COALESCE(MAX(seq_no), 0)
            FROM agent_messages
            WHERE session_id = #{conversationId}
            """)
    int maxMessageSeq(@Param("conversationId") Long conversationId);

    @Insert("""
            INSERT INTO agent_messages(
                message_id, session_id, turn_id, role, message_kind, content_markdown,
                render_blocks_json, tool_call_id, approval_id, delivery_status, seq_no
            ) VALUES (
                #{messageId}, #{conversationId}, NULL, #{role},
                COALESCE(#{userMessageType}, 'CHAT'), #{contentMd},
                #{attachmentsJson}, NULL, NULL, 'FINAL', #{seqNo}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMessage(AgentMessage message);

    @Update("""
            UPDATE agent_sessions
            SET last_message_at = CURRENT_TIMESTAMP(3),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE session_id = #{conversationId} AND deleted_at IS NULL
            """)
    int touchConversationLastMessage(@Param("conversationId") Long conversationId);

    @Insert("""
            INSERT INTO agent_tasks(
                task_id, session_id, turn_id, project_id, task_type, task_status,
                request_context_id, result_id, active_approval_id, stream_channel_key, trace_id,
                started_at, finished_at
            ) VALUES (
                #{taskId}, #{conversationId}, 0, #{projectId}, #{taskType}, #{status},
                NULL, NULL, NULL, NULL, #{traceId},
                #{startedAt}, #{finishedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertGenerationTask(AgentGenerationTask task);

    @Select("""
            SELECT id,
                   task_id,
                   project_id,
                   session_id AS conversation_id,
                   NULL AS chapter_id,
                   NULL AS model_config_id,
                   task_type,
                   NULL AS prompt_snapshot,
                   NULL AS plugin_snapshot,
                   NULL AS token_usage_json,
                   NULL AS cost_json,
                   trace_id,
                   task_status AS status,
                   started_at,
                   finished_at,
                   NULL AS error_msg,
                   created_at
            FROM agent_tasks
            WHERE project_id = #{projectId} AND task_id = #{taskId}
            LIMIT 1
            """)
    AgentGenerationTask findGenerationTask(@Param("projectId") Long projectId,
                                           @Param("taskId") Long taskId);

    @Update("""
            UPDATE agent_tasks
            SET task_status = #{status},
                finished_at = CASE WHEN #{status} IN ('done', 'applied', 'failed', 'cancelled') THEN CURRENT_TIMESTAMP(3) ELSE finished_at END,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND task_id = #{taskId}
            """)
    int updateGenerationTaskStatus(@Param("projectId") Long projectId,
                                   @Param("taskId") Long taskId,
                                   @Param("status") String status,
                                   @Param("errorMsg") String errorMsg);

    @Update("""
            UPDATE agent_tasks
            SET trace_id = COALESCE(#{traceId}, trace_id),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND task_id = #{taskId}
            """)
    int updateGenerationTaskRuntime(@Param("projectId") Long projectId,
                                    @Param("taskId") Long taskId,
                                    @Param("tokenUsageJson") String tokenUsageJson,
                                    @Param("costJson") String costJson,
                                    @Param("traceId") String traceId);
}
