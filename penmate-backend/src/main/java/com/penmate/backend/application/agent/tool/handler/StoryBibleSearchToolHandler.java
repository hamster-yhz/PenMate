package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.StoryBibleSearchApplicationService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import org.springframework.stereotype.Component;

@Component
public class StoryBibleSearchToolHandler implements AgentToolHandler {
    private final StoryBibleSearchApplicationService service;

    public StoryBibleSearchToolHandler(StoryBibleSearchApplicationService service) {
        this.service = service;
    }

    @Override public String toolCode() { return "story_bible_search"; }

    @Override
    public void validate(ToolCallRequest request) {
        if (request == null || request.runId() == null || request.projectId() == null) {
            throw new IllegalArgumentException("Run context is required for Story Bible search");
        }
    }

    @Override public ToolCallResult execute(ToolCallRequest request) { return service.execute(request); }
}
