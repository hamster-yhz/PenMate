package com.penmate.backend.domain.agent.run.model;

import java.util.Locale;

public enum AgentRunContinuationStage {
    READY_FOR_LLM,
    READY_FOR_TOOL,
    COMPLETED;

    public static AgentRunContinuationStage from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Agent Run continuation stage is required");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
