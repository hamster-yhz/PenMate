package com.penmate.backend.domain.agent.model;

public enum AgentLlmMessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL;

    public String wireValue() {
        return name().toLowerCase();
    }
}
