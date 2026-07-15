package com.penmate.backend.application.agent.context;

import java.util.List;
import java.util.Map;

public record StoryBibleRouteDecision(
        StoryBibleRoutingMode mode,
        List<Long> selectedNodeIds,
        Map<Long, String> reasons,
        boolean selectorUsed,
        long selectorLatencyMillis,
        boolean semanticUnavailable,
        List<String> missingFlags
) {
    public StoryBibleRouteDecision {
        selectedNodeIds = List.copyOf(selectedNodeIds == null ? List.of() : selectedNodeIds);
        reasons = Map.copyOf(reasons == null ? Map.of() : reasons);
        missingFlags = List.copyOf(missingFlags == null ? List.of() : missingFlags);
    }
}
