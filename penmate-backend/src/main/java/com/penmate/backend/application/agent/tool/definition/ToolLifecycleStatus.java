package com.penmate.backend.application.agent.tool.definition;

/** Controls whether a tool can enter new Runs and whether existing calls may execute. */
public enum ToolLifecycleStatus {
    ACTIVE,
    DRAINING,
    DISABLED;

    public boolean selectableForNewRuns() {
        return this == ACTIVE;
    }

    public boolean executable() {
        return this != DISABLED;
    }
}
