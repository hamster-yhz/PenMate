package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentConversation;
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
                bound_style_id, story_bible_routing_mode, router_model_config_id, active_context_epoch_id,
                last_turn_id, last_run_id, last_message_at, resumed_at
            ) VALUES (
                #{conversationId}, #{projectId}, #{userId}, #{title}, #{status},
                NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
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
}
