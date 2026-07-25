package com.penmate.backend.application.agent.llm;

public interface AgentLlmGateway {

    AgentLlmTurnResponse generateTurn(AgentLlmTurnRequest request,
                                      AgentLlmExecutionConfig executionConfig);

    default boolean supportsStreaming(AgentLlmExecutionConfig executionConfig) {
        return false;
    }

    default AgentLlmCapabilities capabilities(AgentLlmExecutionConfig executionConfig) {
        return new AgentLlmCapabilities(AgentLlmProtocol.UNKNOWN, false, false,
                false, false, false);
    }

    default AgentLlmTurnResponse streamTurn(AgentLlmTurnRequest request,
                                            AgentLlmExecutionConfig executionConfig,
                                            AgentLlmStreamObserver observer) {
        return generateTurn(request, executionConfig);
    }
}
