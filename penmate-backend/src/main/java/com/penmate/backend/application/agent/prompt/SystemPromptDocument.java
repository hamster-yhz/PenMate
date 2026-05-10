package com.penmate.backend.application.agent.prompt;

public record SystemPromptDocument(
        String fileName,
        String path,
        String content
) {
}
