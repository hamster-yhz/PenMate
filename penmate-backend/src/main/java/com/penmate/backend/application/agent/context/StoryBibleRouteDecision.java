package com.penmate.backend.application.agent.context;

import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;

import java.util.List;
import java.util.Map;

public record StoryBibleRouteDecision(
        StoryBibleRoutingMode mode,
        List<String> intentTags,
        List<Long> selectedNodeIds,
        List<Long> relationExpansionNodeIds,
        Map<Long, String> reasons,
        boolean selectorUsed,
        long selectorLatencyMillis,
        double selectorConfidence,
        LlmTokenUsage selectorTokenUsage,
        boolean semanticUnavailable,
        List<String> missingFlags
) {
    public StoryBibleRouteDecision {
        intentTags = List.copyOf(intentTags == null ? List.of() : intentTags);
        selectedNodeIds = List.copyOf(selectedNodeIds == null ? List.of() : selectedNodeIds);
        relationExpansionNodeIds = List.copyOf(relationExpansionNodeIds == null ? List.of() : relationExpansionNodeIds);
        reasons = Map.copyOf(reasons == null ? Map.of() : reasons);
        selectorTokenUsage = selectorTokenUsage == null ? LlmTokenUsage.ZERO : selectorTokenUsage;
        missingFlags = List.copyOf(missingFlags == null ? List.of() : missingFlags);
    }

    public StoryBibleRouteDecision(StoryBibleRoutingMode mode, List<Long> selectedNodeIds,
                                   Map<Long, String> reasons, boolean selectorUsed,
                                   long selectorLatencyMillis, boolean semanticUnavailable,
                                   List<String> missingFlags) {
        this(mode, List.of(), selectedNodeIds, List.of(), reasons, selectorUsed, selectorLatencyMillis,
                0d, LlmTokenUsage.ZERO, semanticUnavailable, missingFlags);
    }
}
