package com.penmate.backend.application.agent.llm;

public class AgentLlmInvocationCancelledException extends RuntimeException {

    public AgentLlmInvocationCancelledException() {
        super("LLM invocation cancelled");
    }
}
