package com.penmate.backend.infrastructure.agent.prompt;

import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import com.penmate.backend.application.agent.prompt.SystemPromptBundle;
import com.penmate.backend.application.agent.prompt.SystemPromptDocument;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ClasspathSkillPromptRegistry implements SkillPromptRegistry {

    private static final Map<String, String> DIRECTORY_ALIASES = Map.of(
            "writer", "writer",
            "scene-writer", "writer",
            "planner", "planner",
            "checker", "checker",
            "continuity-checker", "checker",
            "continuity-check", "checker",
            "editor", "editor",
            "story-bible", "story-bible",
            "story-bible-query", "story-bible",
            "story-bible-guard", "story-bible"
    );

    private final SystemPromptProvider systemPromptProvider;

    public ClasspathSkillPromptRegistry(SystemPromptProvider systemPromptProvider) {
        this.systemPromptProvider = systemPromptProvider;
    }

    @Override
    public SystemPromptDocument load(String skill) {
        String canonicalSkill = canonicalize(skill);
        String directory = resolveDirectory(canonicalSkill);
        SystemPromptBundle bundle;
        try {
            bundle = systemPromptProvider.loadBundle("skills", directory);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Unsupported skill prompt: " + skill + " -> " + directory, ex);
        }
        if (bundle.documents().isEmpty()) {
            throw new IllegalArgumentException("No prompt documents found for skill: " + skill);
        }
        if (bundle.documents().size() == 1) {
            return bundle.documents().get(0);
        }
        return new SystemPromptDocument(
                directory + ".md",
                bundle.documents().stream().map(SystemPromptDocument::path).collect(Collectors.joining(",")),
                bundle.assembledPrompt()
        );
    }

    private String resolveDirectory(String canonicalSkill) {
        String alias = DIRECTORY_ALIASES.get(canonicalSkill);
        if (alias != null) {
            return alias;
        }
        if (canonicalSkill.startsWith("story-bible")) {
            return "story-bible";
        }
        if (canonicalSkill.endsWith("checker") || canonicalSkill.endsWith("check")) {
            return "checker";
        }
        if (canonicalSkill.endsWith("writer")) {
            return "writer";
        }
        if (canonicalSkill.contains("planner")) {
            return "planner";
        }
        if (canonicalSkill.contains("editor")) {
            return "editor";
        }
        return canonicalSkill;
    }

    private String canonicalize(String skill) {
        if (skill == null || skill.isBlank()) {
            throw new IllegalArgumentException("skill must not be blank");
        }
        return skill.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace(' ', '-')
                .replaceAll("-+", "-");
    }
}
