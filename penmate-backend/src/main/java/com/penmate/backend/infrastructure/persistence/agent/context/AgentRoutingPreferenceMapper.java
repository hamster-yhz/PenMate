package com.penmate.backend.infrastructure.persistence.agent.context;

import com.penmate.backend.domain.agent.context.model.AgentRoutingPreference;
import org.apache.ibatis.annotations.Select;

public interface AgentRoutingPreferenceMapper {

    @Select("""
            SELECT project_id, story_bible_routing_mode, rag_enabled, router_model_config_id,
                   embedding_model_config_id, index_status
            FROM project_ai_configurations
            WHERE project_id = #{projectId}
            """)
    AgentRoutingPreference findProjectPreference(Long projectId);

}
