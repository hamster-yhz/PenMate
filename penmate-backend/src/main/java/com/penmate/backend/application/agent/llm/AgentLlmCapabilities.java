package com.penmate.backend.application.agent.llm;

public record AgentLlmCapabilities(
        AgentLlmProtocol protocol,
        boolean streaming,
        boolean tools,
        boolean reasoningSummaries,
        boolean assistantPhases,
        boolean opaqueReasoningContinuation
) {
    public AgentLlmCapabilities {
        protocol = protocol == null ? AgentLlmProtocol.UNKNOWN : protocol;
    }
}
