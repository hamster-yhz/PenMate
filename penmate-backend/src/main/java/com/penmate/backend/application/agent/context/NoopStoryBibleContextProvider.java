package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;
import org.springframework.stereotype.Component;

@Component
public class NoopStoryBibleContextProvider implements StoryBibleContextProvider {

    @Override
    public StoryBibleContextResult loadContext(Long projectId,
                                               Long conversationId,
                                               Long chapterId,
                                               String userMessage,
                                               AgentPreflightDecision decision) {
        return StoryBibleContextResult.noop();
    }
}
