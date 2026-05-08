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
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            ORDER BY COALESCE(last_message_at, created_at) DESC, id DESC
            """)
    List<AgentConversation> listConversationSummaries(@Param("projectId") Long projectId);

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
    int insertConversationSummary(AgentConversation conversation);

    @Select("""
            SELECT id,
                   session_id,
                   project_id,
                   owner_user_id,
                   title,
                   session_status,
                   bound_style_id,
                   active_context_version,
                   last_turn_id,
                   last_task_id,
                   resumed_at,
                   created_at,
                   updated_at
            FROM agent_sessions
            WHERE project_id = #{projectId} AND session_id = #{sessionId} AND deleted_at IS NULL
            LIMIT 1
            """)
    AgentSession findSession(@Param("projectId") Long projectId,
                             @Param("sessionId") Long sessionId);

    @Update("""
            UPDATE agent_sessions
            SET bound_style_id = #{styleId}
            WHERE project_id = #{projectId} AND session_id = #{sessionId} AND deleted_at IS NULL
            """)
    int updateBoundStyle(@Param("projectId") Long projectId,
                         @Param("sessionId") Long sessionId,
                         @Param("styleId") Long styleId);

    @Insert("""
            INSERT INTO agent_session_style_bindings(binding_id, session_id, style_id, source)
            VALUES (#{bindingId}, #{sessionId}, #{styleId}, 'MANUAL_SWITCH')
            """)
    int insertStyleBinding(@Param("bindingId") Long bindingId,
                           @Param("sessionId") Long sessionId,
                           @Param("styleId") Long styleId);
}
