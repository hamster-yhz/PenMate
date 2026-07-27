package com.penmate.backend.application.agent.prompt;

import java.util.List;

public record SystemPromptBundle(
        String stage,
        List<SystemPromptDocument> documents,
        String assembledPrompt
) {
}
