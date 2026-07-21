package com.penmate.backend.interfaces.api.agent.dto;

/**
 * Agent 会话对外响应 DTO。
 * <p>仅暴露工作台需要的会话业务语义字段，不泄漏历史 conversationId 命名。</p>
 */
public record AgentSessionDto(
        String sessionId,
        String title,
        String status,
        String lastRunStatus,
        String lastMessageAt,
        String createdAt,
        String updatedAt,
        String deletedAt
) {
}
