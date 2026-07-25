package com.penmate.backend.application.agent.prompt;

import java.util.List;

public interface SkillPromptRegistry {

    List<SkillCatalogItem> listAvailableSkills();

    LoadedSkill load(String skill);
}
