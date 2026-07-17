package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.StoryBibleUpdateApplicationService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import org.springframework.stereotype.Component;

/**
 * Story Bible update tool 处理器。
 */
@Component
public class StoryBibleUpdateToolHandler implements AgentToolHandler {

    private final StoryBibleUpdateApplicationService storyBibleUpdateApplicationService;

    public StoryBibleUpdateToolHandler(StoryBibleUpdateApplicationService storyBibleUpdateApplicationService) {
        this.storyBibleUpdateApplicationService = storyBibleUpdateApplicationService;
    }

    @Override
    public String toolCode() {
        return "story_bible_update";
    }

    @Override
    public boolean mutatesState(ToolCallRequest request) {
        return true;
    }

    @Override
    public void validate(ToolCallRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
    }

    @Override
    public ToolCallResult execute(ToolCallRequest request) {
        if (request == null) {
            return new ToolCallResult("FAILED", null, null, "STORY_BIBLE_UPDATE_FAILED", "request must not be null");
        }
        try {
            return storyBibleUpdateApplicationService.execute(request);
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "story bible update execution failed"
                    : ex.getMessage();
            return new ToolCallResult("FAILED", null, null, "STORY_BIBLE_UPDATE_FAILED", message);
        }
    }
}
