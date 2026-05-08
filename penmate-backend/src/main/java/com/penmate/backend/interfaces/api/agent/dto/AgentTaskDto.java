package com.penmate.backend.interfaces.api.agent.dto;

/**
 * Agent turn 创建后返回的任务 DTO。
 */
public record AgentTaskDto(
        AgentRecoverySnapshotDto.SessionDto session,
        AgentRecoverySnapshotDto.ActiveTaskDto activeTask,
        String taskType,
        String userMessage
) {
}
