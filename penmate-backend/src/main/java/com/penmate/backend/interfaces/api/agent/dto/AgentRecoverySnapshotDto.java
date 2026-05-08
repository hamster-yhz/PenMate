package com.penmate.backend.interfaces.api.agent.dto;

import java.util.List;

/**
 * Agent recovery snapshot API DTO。
 * <p>该 DTO 用于冻结 session / activeTask / pendingApproval / messages / workbenchContext 的接口返回结构，
 * 避免控制器直接暴露临时内部视图类。</p>
 */
public record AgentRecoverySnapshotDto(
        SessionDto session,
        ActiveTaskDto activeTask,
        Object pendingApproval,
        List<Object> messages,
        Object workbenchContext
) {

    /**
     * 会话摘要 DTO。
     */
    public record SessionDto(
            Long sessionId,
            String title,
            String status,
            BoundStyleDto boundStyle,
            String lastTaskStatus
    ) {
    }

    /**
     * 会话绑定风格摘要 DTO。
     */
    public record BoundStyleDto(
            Long styleId,
            String name
    ) {
    }

    /**
     * 当前激活任务摘要 DTO。
     */
    public record ActiveTaskDto(
            Long taskId,
            String taskStatus,
            Long requestContextId
    ) {
    }
}
