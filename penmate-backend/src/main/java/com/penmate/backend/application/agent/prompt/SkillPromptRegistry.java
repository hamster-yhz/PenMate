package com.penmate.backend.application.agent.prompt;

public interface SkillPromptRegistry {

    SystemPromptDocument load(String skill);
}
