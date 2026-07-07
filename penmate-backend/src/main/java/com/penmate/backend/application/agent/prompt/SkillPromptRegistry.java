package com.penmate.backend.application.agent.prompt;

import java.util.List;

public interface SkillPromptRegistry {

    List<String> listAvailableSkills();

    SystemPromptDocument load(String skill);
}
