package com.penmate.backend.infrastructure.persistence.agent.context;

import com.penmate.backend.domain.agent.context.model.AgentRoutingPreference;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AgentRoutingPreferenceMapper {

    @Select("""
            SELECT project_id, story_bible_routing_mode, router_model_config_id,
                   embedding_model_config_id, index_status
            FROM project_ai_configurations
            WHERE project_id = #{projectId}
            """)
    AgentRoutingPreference findProjectPreference(Long projectId);

    @Update("""
            UPDATE project_ai_configurations
            SET story_bible_routing_mode = #{routingMode}, router_model_config_id = #{routerModelConfigId},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId}
            """)
    int updateProjectPreference(@Param("projectId") Long projectId,
                                @Param("routingMode") String routingMode,
                                @Param("routerModelConfigId") Long routerModelConfigId);
}
