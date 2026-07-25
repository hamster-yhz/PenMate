package com.penmate.backend.application.agent.prompt;

import java.util.Objects;

public record LoadedSkill(
        SkillCatalogItem descriptor,
        SystemPromptDocument instructions
) {

    public LoadedSkill {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(instructions, "instructions");
    }
}
