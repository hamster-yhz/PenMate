package com.penmate.backend.infrastructure.persistence.agent.context;

import com.penmate.backend.domain.agent.context.model.AgentRoutingPreference;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AgentRoutingPreferenceMapper {

    @Select("""
            SELECT user_id, story_bible_routing_mode, router_model_config_id
            FROM agent_user_preferences WHERE user_id = #{userId} LIMIT 1
            """)
    AgentRoutingPreference findUserPreference(Long userId);

    @Insert("""
            INSERT INTO agent_user_preferences(user_id, story_bible_routing_mode, router_model_config_id)
            VALUES(#{userId}, #{storyBibleRoutingMode}, #{routerModelConfigId})
            ON CONFLICT (user_id) DO UPDATE SET
                story_bible_routing_mode = EXCLUDED.story_bible_routing_mode,
                router_model_config_id = EXCLUDED.router_model_config_id,
                updated_at = CURRENT_TIMESTAMP(3)
            """)
    int upsertUserPreference(AgentRoutingPreference preference);

    @Update("""
            UPDATE agent_sessions
            SET story_bible_routing_mode = #{routingMode}, router_model_config_id = #{routerModelConfigId}
            WHERE project_id = #{projectId} AND session_id = #{sessionId} AND deleted_at IS NULL
            """)
    int updateSessionOverride(@Param("projectId") Long projectId, @Param("sessionId") Long sessionId,
                              @Param("routingMode") String routingMode,
                              @Param("routerModelConfigId") Long routerModelConfigId);
}
