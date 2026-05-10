package com.penmate.backend.application.agent.orchestration.preflight;

public interface AgentPreflightCoordinator {

    AgentPreflightDecision coordinate(AgentPreflightRequest request);
}
