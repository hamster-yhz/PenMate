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
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface AgentMapper {

    @Select("""
            SELECT id, conversation_id, project_id, user_id, title,
                   CAST(context_scope_json AS CHAR) AS context_scope_json,
                   last_message_at, status, created_at, updated_at, deleted_at
            FROM agent_conversations
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            ORDER BY id DESC
            """)
    List<AgentConversation> listConversations(@Param("projectId") Long projectId);

    @Select("""
            SELECT id, conversation_id, project_id, user_id, title,
                   CAST(context_scope_json AS CHAR) AS context_scope_json,
                   last_message_at, status, created_at, updated_at, deleted_at
            FROM agent_conversations
            WHERE project_id = #{projectId} AND conversation_id = #{conversationId} AND deleted_at IS NULL
            LIMIT 1
            """)
    AgentConversation findConversation(@Param("projectId") Long projectId,
                                       @Param("conversationId") Long conversationId);

    @Insert("""
            INSERT INTO agent_conversations(conversation_id, project_id, user_id, title, context_scope_json, status)
            VALUES (#{conversationId}, #{projectId}, #{userId}, #{title}, #{contextScopeJson}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertConversation(AgentConversation conversation);

    @Select("""
            SELECT id, message_id, conversation_id, role, user_message_type, content_md,
                   CAST(attachments_json AS CHAR) AS attachments_json,
                   CAST(tool_calls_json AS CHAR) AS tool_calls_json,
                   seq_no, created_at
            FROM agent_messages
            WHERE conversation_id = #{conversationId}
            ORDER BY seq_no ASC, id ASC
            """)
    List<AgentMessage> listMessages(@Param("conversationId") Long conversationId);

    @Select("""
            SELECT COALESCE(MAX(seq_no), 0)
            FROM agent_messages
            WHERE conversation_id = #{conversationId}
            """)
    int maxMessageSeq(@Param("conversationId") Long conversationId);

    @Insert("""
            INSERT INTO agent_messages(message_id, conversation_id, role, user_message_type, content_md, attachments_json, tool_calls_json, seq_no)
            VALUES (#{messageId}, #{conversationId}, #{role}, #{userMessageType}, #{contentMd}, #{attachmentsJson}, #{toolCallsJson}, #{seqNo})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMessage(AgentMessage message);

    @Update("""
            UPDATE agent_conversations
            SET last_message_at = CURRENT_TIMESTAMP(3),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE conversation_id = #{conversationId} AND deleted_at IS NULL
            """)
    int touchConversationLastMessage(@Param("conversationId") Long conversationId);

    @Insert("""
            INSERT INTO agent_generation_tasks(
                task_id, project_id, conversation_id, chapter_id, model_config_id, task_type,
                prompt_snapshot, style_profile_snapshot, plugin_snapshot,
                token_usage_json, cost_json, trace_id,
                status, started_at, finished_at, error_msg
            ) VALUES (
                #{taskId}, #{projectId}, #{conversationId}, #{chapterId}, #{modelConfigId}, #{taskType},
                #{promptSnapshot}, #{styleProfileSnapshot}, #{pluginSnapshot},
                #{tokenUsageJson}, #{costJson}, #{traceId},
                #{status}, #{startedAt}, #{finishedAt}, #{errorMsg}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertGenerationTask(AgentGenerationTask task);

    @Select("""
            SELECT id, task_id, project_id, conversation_id, chapter_id,
                   model_config_id, task_type,
                   CAST(prompt_snapshot AS CHAR) AS prompt_snapshot,
                   CAST(style_profile_snapshot AS CHAR) AS style_profile_snapshot,
                   CAST(plugin_snapshot AS CHAR) AS plugin_snapshot,
                   CAST(token_usage_json AS CHAR) AS token_usage_json,
                   CAST(cost_json AS CHAR) AS cost_json,
                   trace_id,
                   status, started_at, finished_at, error_msg, created_at
            FROM agent_generation_tasks
            WHERE project_id = #{projectId} AND task_id = #{taskId}
            LIMIT 1
            """)
    AgentGenerationTask findGenerationTask(@Param("projectId") Long projectId,
                                           @Param("taskId") Long taskId);

    @Update("""
            UPDATE agent_generation_tasks
            SET status = #{status},
                error_msg = #{errorMsg},
                finished_at = CASE WHEN #{status} IN ('done', 'applied', 'failed', 'cancelled') THEN CURRENT_TIMESTAMP(3) ELSE finished_at END
            WHERE project_id = #{projectId} AND task_id = #{taskId}
            """)
    int updateGenerationTaskStatus(@Param("projectId") Long projectId,
                                   @Param("taskId") Long taskId,
                                   @Param("status") String status,
                                   @Param("errorMsg") String errorMsg);

    @Update("""
            UPDATE agent_generation_tasks
            SET token_usage_json = COALESCE(#{tokenUsageJson}, token_usage_json),
                cost_json = COALESCE(#{costJson}, cost_json),
                trace_id = COALESCE(#{traceId}, trace_id)
            WHERE project_id = #{projectId} AND task_id = #{taskId}
            """)
    int updateGenerationTaskRuntime(@Param("projectId") Long projectId,
                                    @Param("taskId") Long taskId,
                                    @Param("tokenUsageJson") String tokenUsageJson,
                                    @Param("costJson") String costJson,
                                    @Param("traceId") String traceId);
}

