package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
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

    @Select("""
            SELECT id AS id,
                   session_id AS sessionId,
                   project_id AS projectId,
                   owner_user_id AS ownerUserId,
                   title AS title,
                   session_status AS sessionStatus,
                   bound_style_id AS boundStyleId,
                   active_context_version AS activeContextVersion,
                   last_turn_id AS lastTurnId,
                   last_task_id AS lastTaskId,
                   resumed_at AS resumedAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM agent_sessions
            WHERE project_id = #{projectId} AND session_id = #{sessionId} AND deleted_at IS NULL
            LIMIT 1
            """)
    Map<String, Object> findSessionRow(@Param("projectId") Long projectId,
                                       @Param("sessionId") Long sessionId);

    @Select("""
            SELECT id
            FROM agent_sessions
            WHERE (#{projectId} IS NULL OR project_id = #{projectId})
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
            SELECT turn_id AS turnId,
                   session_id AS sessionId,
                   turn_seq AS turnSeq,
                   user_message_id AS userMessageId,
                   assistant_message_id AS assistantMessageId,
                   task_id AS taskId,
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
                turn_id, session_id, turn_seq, user_message_id, assistant_message_id, task_id, turn_status, resume_token
            ) VALUES (
                #{turnId}, #{sessionId}, #{turnSeq}, #{userMessageId}, NULL, #{taskId}, #{turnStatus}, #{resumeToken}
            )
            """)
    int insertTurn(@Param("sessionId") Long sessionId,
                   @Param("turnId") Long turnId,
                   @Param("turnSeq") Integer turnSeq,
                   @Param("userMessageId") Long userMessageId,
                   @Param("taskId") Long taskId,
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

    @Select("""
            SELECT turn_id AS turnId,
                   task_id AS taskId,
                   task_status AS taskStatus,
                   request_context_id AS requestContextId,
                   active_approval_id AS activeApprovalId
            FROM agent_tasks
            WHERE session_id = #{sessionId}
              AND task_id = #{taskId}
            LIMIT 1
            """)
    Map<String, Object> findTaskRow(@Param("sessionId") Long sessionId,
                                    @Param("taskId") Long taskId);

    @Select("""
            SELECT turn_id AS turnId,
                   task_id AS taskId,
                   task_status AS taskStatus,
                   request_context_id AS requestContextId,
                   active_approval_id AS activeApprovalId
            FROM agent_tasks
            WHERE project_id = #{projectId}
              AND session_id = #{sessionId}
              AND turn_id = #{turnId}
            LIMIT 1
            """)
    Map<String, Object> findTaskRowByTurnId(@Param("projectId") Long projectId,
                                            @Param("sessionId") Long sessionId,
                                            @Param("turnId") Long turnId);

    @Insert("""
            INSERT INTO agent_tasks(
                task_id, session_id, turn_id, project_id, task_type, task_status,
                prompt_snapshot, request_context_id, result_id, active_approval_id, stream_channel_key, trace_id
            ) VALUES (
                #{taskId}, #{sessionId}, #{turnId}, #{projectId}, #{taskType}, #{taskStatus},
                #{promptSnapshot}, #{requestContextId}, NULL, NULL, NULL, #{traceId}
            )
            """)
    int insertRuntimeTask(@Param("taskId") Long taskId,
                          @Param("sessionId") Long sessionId,
                          @Param("turnId") Long turnId,
                          @Param("projectId") Long projectId,
                          @Param("taskType") String taskType,
                          @Param("taskStatus") String taskStatus,
                          @Param("promptSnapshot") String promptSnapshot,
                          @Param("requestContextId") Long requestContextId,
                          @Param("traceId") String traceId);

    @Update("""
            UPDATE agent_tasks
            SET turn_id = #{turnId},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND task_id = #{taskId}
            """)
    int updateRuntimeTaskTurnLink(@Param("projectId") Long projectId,
                                  @Param("taskId") Long taskId,
                                  @Param("turnId") Long turnId);

    @Select("""
            SELECT context_id AS contextId,
                   task_id AS taskId,
                   chapter_id AS chapterId,
                   selected_text AS selectedText,
                   outline_snapshot_json AS outlineSnapshotJson,
                   cards_snapshot_json AS cardsSnapshotJson,
                   rag_snapshot_json AS ragSnapshotJson,
                   plugin_bindings_json AS pluginBindingsJson,
                   style_snapshot_json AS styleSnapshotJson,
                   model_snapshot_json AS modelSnapshotJson,
                   context_hash AS contextHash
            FROM agent_task_contexts
            WHERE task_id = #{taskId}
            LIMIT 1
            """)
    Map<String, Object> findTaskContextRow(@Param("taskId") Long taskId);

    @Insert("""
            INSERT INTO agent_task_contexts(
                context_id, task_id, chapter_id, selected_text,
                outline_snapshot_json, cards_snapshot_json, rag_snapshot_json,
                plugin_bindings_json, style_snapshot_json, model_snapshot_json, context_hash
            ) VALUES (
                #{contextId}, #{taskId}, #{chapterId}, #{selectedText},
                #{outlineSnapshotJson}, #{cardsSnapshotJson}, #{ragSnapshotJson},
                #{pluginBindingsJson}, #{styleSnapshotJson}, #{modelSnapshotJson}, #{contextHash}
            )
            """)
    int insertTaskContext(AgentTaskContext taskContext);

    @Select("""
            SELECT message_id AS messageId,
                   role AS role,
                   message_kind AS messageKind,
                   content_markdown AS contentMarkdown,
                   approval_id AS approvalId,
                   seq_no AS seqNo,
                   created_at AS createdAt
            FROM agent_messages
            WHERE session_id = #{sessionId}
            ORDER BY seq_no ASC, id ASC
            """)
    List<Map<String, Object>> listMessageRows(@Param("sessionId") Long sessionId);

    @Insert("""
            INSERT INTO agent_sessions(
                session_id, project_id, owner_user_id, title, session_status,
                bound_style_id, active_context_version, last_turn_id, last_task_id, last_message_at, resumed_at
            ) VALUES (
                #{sessionId}, #{projectId}, #{ownerUserId}, #{title}, #{sessionStatus},
                #{boundStyleId}, 1, #{lastTurnId}, #{lastTaskId}, NULL, #{resumedAt}
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
            SET last_task_id = #{taskId}
            WHERE project_id = #{projectId} AND session_id = #{sessionId} AND deleted_at IS NULL
            """)
    int updateLastRunningTask(@Param("projectId") Long projectId,
                              @Param("sessionId") Long sessionId,
                              @Param("taskId") Long taskId);

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
