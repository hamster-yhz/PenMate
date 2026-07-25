package com.penmate.backend.application.agent.tool.runtime;

public class AgentRunExecutionRejectedException extends RuntimeException {
    private final String errorCode;

    public AgentRunExecutionRejectedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
