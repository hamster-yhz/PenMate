package com.penmate.backend.domain.agent.model;

public enum AgentSafetyMode {
    STRICT(0),
    STANDARD(2),
    AUTONOMOUS(3),
    FULL_AUTHORITY(4);

    private final int maximumAutomaticRisk;

    AgentSafetyMode(int maximumAutomaticRisk) { this.maximumAutomaticRisk = maximumAutomaticRisk; }
    public int maximumAutomaticRisk() { return maximumAutomaticRisk; }

    public static AgentSafetyMode parse(String value) {
        if (value == null || value.isBlank()) return STANDARD;
        return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
