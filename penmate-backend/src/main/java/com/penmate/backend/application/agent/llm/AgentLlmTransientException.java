package com.penmate.backend.application.agent.llm;

/**
 * Signals a temporary provider or transport failure that can be retried by the Agent Run lease policy.
 */
public class AgentLlmTransientException extends RuntimeException {

    public AgentLlmTransientException(String message) {
        super(message);
    }

    public AgentLlmTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
