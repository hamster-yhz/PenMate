package com.penmate.backend.application.agent.prompt;

import java.util.List;

public record SystemPromptBundle(
        String stage,
        String profile,
        List<SystemPromptDocument> documents,
        String assembledPrompt
) {
}
