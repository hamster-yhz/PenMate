package com.penmate.backend.domain.agent.run.model;

import java.util.Locale;

public enum AgentToolCallExecutionStatus {
    STARTED,
    SUCCEEDED,
    FAILED,
    AMBIGUOUS;

    public static AgentToolCallExecutionStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Tool call execution status is required");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public boolean isTerminal() {
        return this != STARTED;
    }
}
