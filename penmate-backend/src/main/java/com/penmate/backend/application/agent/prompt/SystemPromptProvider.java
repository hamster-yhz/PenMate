package com.penmate.backend.application.agent.prompt;

public interface SystemPromptProvider {

    SystemPromptBundle loadBundle(String stage, String profile);
}
