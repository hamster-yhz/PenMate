package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.llm.LlmTokenUsage;

/**
 * Agent tool loop 单次闭环执行结果。
 */
public record AgentToolLoopIterationResult(
        String finalAssistantText,
        boolean waitingApproval,
        Long approvalId,
        int toolCallCount,
        String toolContext,
        LlmTokenUsage tokenUsage
) {

    public AgentToolLoopIterationResult {
        finalAssistantText = finalAssistantText == null ? "" : finalAssistantText;
        toolContext = toolContext == null ? "" : toolContext;
        tokenUsage = tokenUsage == null ? LlmTokenUsage.ZERO : tokenUsage;
    }

    public static AgentToolLoopIterationResult completed(String finalAssistantText,
                                                         int toolCallCount,
                                                         String toolContext) {
        return completed(finalAssistantText, toolCallCount, toolContext, LlmTokenUsage.ZERO);
    }

    public static AgentToolLoopIterationResult completed(String finalAssistantText,
                                                         int toolCallCount,
                                                         String toolContext,
                                                         LlmTokenUsage tokenUsage) {
        return new AgentToolLoopIterationResult(finalAssistantText, false, null, toolCallCount, toolContext, tokenUsage);
    }

    public static AgentToolLoopIterationResult waitingApproval(Long approvalId,
                                                               int toolCallCount,
                                                               String toolContext) {
        return waitingApproval(approvalId, toolCallCount, toolContext, LlmTokenUsage.ZERO);
    }

    public static AgentToolLoopIterationResult waitingApproval(Long approvalId,
                                                               int toolCallCount,
                                                               String toolContext,
                                                               LlmTokenUsage tokenUsage) {
        return new AgentToolLoopIterationResult("", true, approvalId, toolCallCount, toolContext, tokenUsage);
    }
}
