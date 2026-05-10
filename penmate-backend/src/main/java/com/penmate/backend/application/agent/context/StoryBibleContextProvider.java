package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;

public interface StoryBibleContextProvider {

    StoryBibleContextResult loadContext(Long projectId,
                                        Long conversationId,
                                        Long chapterId,
                                        String userMessage,
                                        AgentPreflightDecision decision);
}
