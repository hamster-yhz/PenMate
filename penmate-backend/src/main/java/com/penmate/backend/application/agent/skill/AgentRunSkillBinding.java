package com.penmate.backend.application.agent.skill;

import java.time.Instant;

public record AgentRunSkillBinding(
        Long runId,
        String skillName,
        String contentHash,
        String activationSource,
        String toolCallId,
        String content,
        Instant activatedAt
) {
}
