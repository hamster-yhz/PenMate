package com.penmate.backend.domain.agent.run.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record AgentRunNoProgressState(
        long toolCallCount,
        List<ToolObservation> observations
) {
    private static final int RETAINED_OBSERVATIONS = 21;

    public static final AgentRunNoProgressState EMPTY = new AgentRunNoProgressState(0, List.of());

    public AgentRunNoProgressState {
        if (toolCallCount < 0) throw new IllegalArgumentException("toolCallCount must not be negative");
        observations = List.copyOf(observations == null ? List.of() : observations);
        if (observations.size() > RETAINED_OBSERVATIONS) {
            throw new IllegalArgumentException("Too many no-progress observations");
        }
        if (toolCallCount < observations.size()) {
            throw new IllegalArgumentException("toolCallCount must cover retained observations");
        }
    }

    public AgentRunNoProgressState append(String signature, Set<String> progressMarkers,
                                          boolean mutationSucceeded) {
        List<ToolObservation> next = new ArrayList<>(observations);
        next.add(new ToolObservation(signature, progressMarkers, mutationSucceeded));
        if (next.size() > RETAINED_OBSERVATIONS) next.removeFirst();
        return new AgentRunNoProgressState(toolCallCount + 1, next);
    }

    public record ToolObservation(String signature, Set<String> progressMarkers,
                                  boolean mutationSucceeded) {
        public ToolObservation {
            signature = signature == null ? "" : signature;
            progressMarkers = Set.copyOf(progressMarkers == null ? Set.of() : progressMarkers);
        }
    }
}
