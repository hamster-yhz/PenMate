package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;

import java.util.List;
import java.util.Objects;

public record AgentContextRoutingRequest(
        Long projectId,
        Long conversationId,
        Long sessionId,
        Long taskId,
        Long chapterId,
        Integer storyBibleVersion,
        List<String> userMentionedEntities,
        String userMessage,
        String styleSnapshot,
        AgentPreflightDecision decision,
        TaskProfile taskProfile
) {

    public AgentContextRoutingRequest {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(taskProfile, "taskProfile");
        userMentionedEntities = List.copyOf(userMentionedEntities == null ? List.of() : userMentionedEntities);
    }

    public AgentContextRoutingRequest(Long projectId,
                                      Long conversationId,
                                      Long chapterId,
                                      String userMessage,
                                      String styleSnapshot,
                                      AgentPreflightDecision decision,
                                      TaskProfile taskProfile) {
        this(
                projectId,
                conversationId,
                conversationId,
                null,
                chapterId,
                null,
                List.of(),
                userMessage,
                styleSnapshot,
                decision,
                taskProfile
        );
    }
}
