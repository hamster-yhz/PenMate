package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;

import java.util.List;
import java.util.Map;

public interface StoryBibleSelectorGateway {
    Selection select(String userMessage, List<StoryBibleRouteRequest.CatalogEntry> catalog,
                     AgentLlmExecutionConfig executionConfig);

    record Selection(List<Long> nodeIds, Map<Long, String> reasons) {
        public Selection {
            nodeIds = List.copyOf(nodeIds == null ? List.of() : nodeIds);
            reasons = Map.copyOf(reasons == null ? Map.of() : reasons);
        }
    }
}
