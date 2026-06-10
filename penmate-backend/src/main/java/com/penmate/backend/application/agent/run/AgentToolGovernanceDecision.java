package com.penmate.backend.application.agent.run;

public record AgentToolGovernanceDecision(
        boolean requiresApproval,
        Long approvalId
) {

    public static AgentToolGovernanceDecision allowed() {
        return new AgentToolGovernanceDecision(false, null);
    }

    public static AgentToolGovernanceDecision waitingApproval(Long approvalId) {
        return new AgentToolGovernanceDecision(true, approvalId);
    }
}
