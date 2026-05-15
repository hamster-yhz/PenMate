package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;

import java.util.List;

public interface StoryBibleContextProvider {

    List<StoryBibleContextEntryView> loadContext(Long projectId,
                                                 Long conversationId,
                                                 Long chapterId,
                                                 String userMessage,
                                                 AgentPreflightDecision decision);
}
