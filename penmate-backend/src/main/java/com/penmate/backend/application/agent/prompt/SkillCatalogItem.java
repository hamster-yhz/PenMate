package com.penmate.backend.application.agent.prompt;

public record SkillCatalogItem(
        String name,
        String description,
        String contentHash
) {

    public SkillCatalogItem(String name, String description) {
        this(name, description, "");
    }
}
