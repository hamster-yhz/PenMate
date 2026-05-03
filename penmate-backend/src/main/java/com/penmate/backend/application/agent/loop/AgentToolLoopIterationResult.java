package com.penmate.backend.application.agent.loop;

/**
 * Agent tool loop 单次闭环执行结果。
 */
public record AgentToolLoopIterationResult(
        String finalAssistantText,
        boolean waitingApproval,
        Long approvalId,
        int toolCallCount,
        String toolContext
) {

    public AgentToolLoopIterationResult {
        finalAssistantText = finalAssistantText == null ? "" : finalAssistantText;
        toolContext = toolContext == null ? "" : toolContext;
    }

    public static AgentToolLoopIterationResult completed(String finalAssistantText,
                                                         int toolCallCount,
                                                         String toolContext) {
        return new AgentToolLoopIterationResult(finalAssistantText, false, null, toolCallCount, toolContext);
    }

    public static AgentToolLoopIterationResult waitingApproval(Long approvalId,
                                                               int toolCallCount,
                                                               String toolContext) {
        return new AgentToolLoopIterationResult("", true, approvalId, toolCallCount, toolContext);
    }
}
