package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;

import java.util.List;
import java.util.Map;

public interface StoryBibleSelectorGateway {
    Selection select(SelectorRequest request, AgentLlmExecutionConfig executionConfig);

    default Selection select(String userMessage, List<StoryBibleRouteRequest.CatalogEntry> catalog,
                             AgentLlmExecutionConfig executionConfig) {
        return select(new SelectorRequest(StoryBibleRoutingMode.LLM_SELECTOR, userMessage, catalog, List.of(), List.of()),
                executionConfig);
    }

    record SelectorRequest(StoryBibleRoutingMode mode, String userMessage,
                           List<StoryBibleRouteRequest.CatalogEntry> catalog,
                           List<Long> workingSetNodeIds,
                           List<AgentLlmMessage> conversationWindow) {
        public SelectorRequest {
            mode = mode == null ? StoryBibleRoutingMode.LLM_SELECTOR : mode;
            userMessage = userMessage == null ? "" : userMessage;
            catalog = List.copyOf(catalog == null ? List.of() : catalog);
            workingSetNodeIds = List.copyOf(workingSetNodeIds == null ? List.of() : workingSetNodeIds);
            conversationWindow = List.copyOf(conversationWindow == null ? List.of() : conversationWindow);
        }

        public SelectorRequest(StoryBibleRoutingMode mode, String userMessage,
                               List<StoryBibleRouteRequest.CatalogEntry> catalog,
                               List<Long> workingSetNodeIds) {
            this(mode, userMessage, catalog, workingSetNodeIds, List.of());
        }
    }

    record Selection(
            List<String> intentTags,
            List<Long> nodeIds,
            List<Long> relationExpansionNodeIds,
            Map<Long, String> reasons,
            List<String> missingContextFlags,
            double confidence,
            LlmTokenUsage tokenUsage
    ) {
        public Selection {
            intentTags = List.copyOf(intentTags == null ? List.of() : intentTags);
            nodeIds = List.copyOf(nodeIds == null ? List.of() : nodeIds);
            relationExpansionNodeIds = List.copyOf(relationExpansionNodeIds == null ? List.of() : relationExpansionNodeIds);
            reasons = Map.copyOf(reasons == null ? Map.of() : reasons);
            missingContextFlags = List.copyOf(missingContextFlags == null ? List.of() : missingContextFlags);
            if (!Double.isFinite(confidence) || confidence < 0d || confidence > 1d) {
                throw new IllegalArgumentException("confidence must be between 0 and 1");
            }
            tokenUsage = tokenUsage == null ? LlmTokenUsage.ZERO : tokenUsage;
        }

        public Selection(List<Long> nodeIds, Map<Long, String> reasons) {
            this(List.of(), nodeIds, List.of(), reasons, List.of(), 0d, LlmTokenUsage.ZERO);
        }
    }
}
