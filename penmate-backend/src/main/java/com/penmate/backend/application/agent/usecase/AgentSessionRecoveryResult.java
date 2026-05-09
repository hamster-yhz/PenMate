package com.penmate.backend.application.agent.usecase;

import java.util.List;

/**
 * Agent 会话恢复应用层结果。
 * <p>用于隔离接口层 recovery DTO，保持应用层只暴露用例结果模型。</p>
 */
public record AgentSessionRecoveryResult(
        SessionView session,
        ActiveTaskView activeTask,
        Object pendingApproval,
        List<Object> messages,
        Object workbenchContext
) {

    public record SessionView(
            Long sessionId,
            String title,
            String status,
            BoundStyleView boundStyle,
            String taskStatus
    ) {
    }

    public record BoundStyleView(
            Long styleId,
            String name
    ) {
    }

    public record ActiveTaskView(
            Long turnId,
            Long taskId,
            String taskStatus,
            Long requestContextId
    ) {
    }
}
