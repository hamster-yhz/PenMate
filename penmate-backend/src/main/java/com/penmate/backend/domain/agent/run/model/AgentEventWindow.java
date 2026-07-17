package com.penmate.backend.domain.agent.run.model;

public record AgentEventWindow(
        Long oldestHotSequence,
        Long latestSequence
) {
    public AgentEventWindow {
        latestSequence = latestSequence == null ? 0L : latestSequence;
        if (latestSequence < 0 || (oldestHotSequence != null && oldestHotSequence < 1)) {
            throw new IllegalArgumentException("Agent Event sequences must be non-negative");
        }
        if (oldestHotSequence != null && oldestHotSequence > latestSequence) {
            throw new IllegalArgumentException("Oldest hot Agent Event cannot exceed latest sequence");
        }
    }

    public boolean requiresResetAfter(Long requestedCursor) {
        long cursor = requestedCursor == null ? 0L : Math.max(0L, requestedCursor);
        if (latestSequence <= cursor) {
            return false;
        }
        if (oldestHotSequence == null) {
            return true;
        }
        return cursor < oldestHotSequence - 1L;
    }
}
