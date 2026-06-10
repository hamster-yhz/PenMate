package com.penmate.backend.application.agent.usecase;

/**
 * Agent turn 应用层返回结果。
 * <p>该结果只表达用例层状态，接口层再负责映射为 HTTP DTO。</p>
 */
public record AgentTurnResult(
        SessionView session,
        ActiveRunView activeRun,
        String taskType,
        String userMessage
) {

    public record SessionView(
            Long sessionId,
            String title,
            String status,
            BoundStyleView boundStyle,
            String lastRunStatus
    ) {
    }

    public record BoundStyleView(
            Long styleId,
            String name
    ) {
    }

    public record ActiveRunView(
            Long turnId,
            Long runId,
            String runStatus,
            String runPhase,
            Long latestSequence
    ) {
    }
}
