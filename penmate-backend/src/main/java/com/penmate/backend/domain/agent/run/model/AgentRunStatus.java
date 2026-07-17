package com.penmate.backend.domain.agent.run.model;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum AgentRunStatus {
    PENDING,
    RUNNING,
    WAITING_APPROVAL,
    SUSPENDED,
    DONE,
    FAILED,
    CANCELLED,
    SUPERSEDED;

    private static final Set<AgentRunStatus> TERMINAL = EnumSet.of(DONE, FAILED, CANCELLED, SUPERSEDED);

    public static AgentRunStatus from(String value) {
        if (value == null || value.isBlank()) return PENDING;
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean isRecoverable() {
        return !isTerminal();
    }

    public boolean canTransitionTo(AgentRunStatus target) {
        if (target == null || isTerminal()) return false;
        if (target == CANCELLED) return true;
        return switch (this) {
            case PENDING -> target == RUNNING || target == FAILED;
            case RUNNING -> target == WAITING_APPROVAL || target == SUSPENDED
                    || target == DONE || target == FAILED || target == SUPERSEDED;
            case WAITING_APPROVAL -> target == RUNNING || target == FAILED || target == SUPERSEDED;
            case SUSPENDED -> target == RUNNING || target == FAILED || target == SUPERSEDED;
            default -> false;
        };
    }
}
