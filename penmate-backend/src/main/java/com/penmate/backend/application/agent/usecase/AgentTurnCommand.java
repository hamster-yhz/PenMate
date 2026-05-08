package com.penmate.backend.application.agent.usecase;

/**
 * Agent turn 应用层命令。
 * <p>用于隔离接口层 DTO，避免应用服务直接依赖 [`CreateAgentTurnDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/CreateAgentTurnDto.java)。</p>
 */
public record AgentTurnCommand(
        Long operatorId,
        String userMessage,
        TaskRequest taskRequest
) {

    public record TaskRequest(
            String taskType,
            Long chapterId,
            String selectedText
    ) {
    }
}
