package com.penmate.backend.infrastructure.persistence.agent.skill;

import com.penmate.backend.application.agent.skill.AgentRunSkillBinding;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentSkillBindingMapper {

    @Select("""
            SELECT skill_name
            FROM agent_session_skill_bindings
            WHERE session_id = #{sessionId}
            ORDER BY skill_name
            """)
    List<String> listSessionSkillNames(@Param("sessionId") Long sessionId);

    @Delete("DELETE FROM agent_session_skill_bindings WHERE session_id = #{sessionId}")
    int deleteSessionBindings(@Param("sessionId") Long sessionId);

    @Insert("""
            INSERT INTO agent_session_skill_bindings(session_id, skill_name)
            VALUES (#{sessionId}, #{skillName})
            """)
    int insertSessionBinding(@Param("sessionId") Long sessionId,
                             @Param("skillName") String skillName);

    @Insert("""
            INSERT INTO agent_skill_snapshots(content_hash, content_text)
            VALUES (#{contentHash}, #{content})
            ON CONFLICT (content_hash) DO NOTHING
            """)
    int saveSnapshot(@Param("contentHash") String contentHash,
                     @Param("content") String content);

    @Select("""
            SELECT b.run_id AS runId,
                   b.skill_name AS skillName,
                   b.content_hash AS contentHash,
                   b.activation_source AS activationSource,
                   b.tool_call_id AS toolCallId,
                   s.content_text AS content,
                   b.activated_at AS activatedAt
            FROM agent_run_skill_bindings b
            LEFT JOIN agent_skill_snapshots s ON s.content_hash = b.content_hash
            WHERE b.run_id = #{runId} AND b.skill_name = #{skillName}
            LIMIT 1
            """)
    AgentRunSkillBinding findRunBinding(@Param("runId") Long runId,
                                        @Param("skillName") String skillName);

    @Select("""
            SELECT b.run_id AS runId,
                   b.skill_name AS skillName,
                   b.content_hash AS contentHash,
                   b.activation_source AS activationSource,
                   b.tool_call_id AS toolCallId,
                   s.content_text AS content,
                   b.activated_at AS activatedAt
            FROM agent_run_skill_bindings b
            LEFT JOIN agent_skill_snapshots s ON s.content_hash = b.content_hash
            WHERE b.run_id = #{runId}
            ORDER BY b.skill_name
            """)
    List<AgentRunSkillBinding> listRunBindings(@Param("runId") Long runId);

    @Select("SELECT COUNT(*) FROM agent_run_skill_bindings WHERE run_id = #{runId}")
    int countRunBindings(@Param("runId") Long runId);

    @Insert("""
            INSERT INTO agent_run_skill_bindings(
                run_id, skill_name, content_hash, activation_source, tool_call_id
            ) VALUES (
                #{runId}, #{skillName}, #{contentHash}, #{activationSource}, #{toolCallId}
            )
            ON CONFLICT (run_id, skill_name) DO NOTHING
            """)
    int insertRunBinding(@Param("runId") Long runId,
                         @Param("skillName") String skillName,
                         @Param("contentHash") String contentHash,
                         @Param("activationSource") String activationSource,
                         @Param("toolCallId") String toolCallId);
}
