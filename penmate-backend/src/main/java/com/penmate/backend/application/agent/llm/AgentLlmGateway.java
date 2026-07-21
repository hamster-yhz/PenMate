package com.penmate.backend.application.agent.llm;

public interface AgentLlmGateway {

    AgentLlmTurnResponse generateTurn(AgentLlmTurnRequest request,
                                      AgentLlmExecutionConfig executionConfig);

    default boolean supportsStreaming(AgentLlmExecutionConfig executionConfig) {
        return false;
    }

    default AgentLlmTurnResponse streamTurn(AgentLlmTurnRequest request,
                                            AgentLlmExecutionConfig executionConfig,
                                            AgentLlmStreamObserver observer) {
        return generateTurn(request, executionConfig);
    }
}
