package com.penmate.backend.application.agent.usecase;

import java.util.List;

/**
 * Agent turn 应用层命令。
 * <p>用于隔离接口层 DTO，避免应用服务直接依赖 [`CreateAgentTurnDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/CreateAgentTurnDto.java)。</p>
 */
public record AgentTurnCommand(
        Long operatorId,
        String userMessage,
        List<String> activeSkills,
        TaskRequest taskRequest
) {

    public AgentTurnCommand {
        activeSkills = activeSkills == null ? null : List.copyOf(activeSkills);
    }

    public record TaskRequest(
            String taskType,
            Long chapterId,
            Long modelConfigId,
            String selectedText
    ) {
    }
}
