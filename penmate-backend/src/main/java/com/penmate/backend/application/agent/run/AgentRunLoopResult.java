package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.llm.LlmTokenUsage;

public record AgentRunLoopResult(
        Status status,
        String finalAssistantText,
        LlmTokenUsage tokenUsage,
        Long approvalId
) {

    public enum Status {
        COMPLETED,
        WAITING_APPROVAL,
        FAILED
    }

    public AgentRunLoopResult {
        status = status == null ? Status.COMPLETED : status;
        finalAssistantText = finalAssistantText == null ? "" : finalAssistantText;
        tokenUsage = tokenUsage == null ? LlmTokenUsage.ZERO : tokenUsage;
    }

    public static AgentRunLoopResult completed(String finalAssistantText, LlmTokenUsage tokenUsage) {
        return new AgentRunLoopResult(Status.COMPLETED, finalAssistantText, tokenUsage, null);
    }

    public static AgentRunLoopResult waitingApproval(Long approvalId, String assistantText, LlmTokenUsage tokenUsage) {
        return new AgentRunLoopResult(Status.WAITING_APPROVAL, assistantText, tokenUsage, approvalId);
    }
}
