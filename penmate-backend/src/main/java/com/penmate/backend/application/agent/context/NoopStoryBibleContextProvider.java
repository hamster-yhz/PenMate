package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NoopStoryBibleContextProvider implements StoryBibleContextProvider {

    @Override
    public List<StoryBibleContextEntryView> loadContext(Long projectId,
                                                        Long conversationId,
                                                        Long chapterId,
                                                        String userMessage,
                                                        AgentPreflightDecision decision) {
        return List.of();
    }
}
