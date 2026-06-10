package com.penmate.backend.application.agent.llm;

public interface AgentLlmGateway {

    AgentLlmTurnResponse generateTurn(AgentLlmTurnRequest request,
                                      AgentLlmExecutionConfig executionConfig);
}
