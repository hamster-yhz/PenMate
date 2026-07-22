package com.penmate.backend.infrastructure.agent.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import com.penmate.backend.application.agent.prompt.SkillCatalogItem;
import com.penmate.backend.application.agent.prompt.SystemPromptBundle;
import com.penmate.backend.application.agent.prompt.SystemPromptDocument;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ClasspathSkillPromptRegistry implements SkillPromptRegistry {

   private static final Logger log = LoggerFactory.getLogger(ClasspathSkillPromptRegistry.class);
   private static final Map<String, String> DIRECTORY_ALIASES = Map.ofEntries(
           Map.entry("writer", "writer"),
           Map.entry("scene_writer", "writer"),
            Map.entry("planner", "planner"),
            Map.entry("checker", "checker"),
            Map.entry("continuity_checker", "checker"),
            Map.entry("continuity_check", "checker"),
            Map.entry("consistency_checker", "checker"),
            Map.entry("editor", "editor"),
            Map.entry("story_bible", "story-bible"),
            Map.entry("story_bible_query", "story-bible"),
            Map.entry("story_bible_guard", "story-bible")
    );
    private static final List<SkillCatalogItem> AVAILABLE_SKILLS = List.of(
            new SkillCatalogItem("writer", "Write prose, narrative scenes, and story passages."),
            new SkillCatalogItem("scene_writer", "Write focused scene-level prose with beats, sensory detail, and pacing."),
            new SkillCatalogItem("planner", "Plan story work, chapters, outlines, and execution steps."),
            new SkillCatalogItem("checker", "Check quality, constraints, coherence, and completeness."),
            new SkillCatalogItem("continuity_checker", "Check continuity across characters, timeline, setting, and established facts."),
            new SkillCatalogItem("editor", "Revise, polish, tighten, and improve existing prose."),
            new SkillCatalogItem("story_bible_query", "Use story bible facts to answer or ground writing decisions."),
            new SkillCatalogItem("story_bible_guard", "Protect canon consistency against story bible constraints.")
    );

    private final SystemPromptProvider systemPromptProvider;

    public ClasspathSkillPromptRegistry(SystemPromptProvider systemPromptProvider) {
        this.systemPromptProvider = systemPromptProvider;
    }

    @Override
    public List<SkillCatalogItem> listAvailableSkills() {
        return AVAILABLE_SKILLS;
    }

    @Override
    public SystemPromptDocument load(String skill) {
        String canonicalSkill = canonicalize(skill);
        String directory = resolveDirectory(skill, canonicalSkill);
        if (directory == null) {
            return null;
        }
        SystemPromptBundle bundle = systemPromptProvider.loadBundle("skills", directory);
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

    private String resolveDirectory(String originalSkill, String canonicalSkill) {
        String directory = DIRECTORY_ALIASES.get(canonicalSkill);
        if (directory == null) {
            log.warn("Unsupported skill prompt: {}, skipping", originalSkill);
            return null;
        }
        return directory;
    }

    private String canonicalize(String skill) {
        if (skill == null || skill.isBlank()) {
            throw new IllegalArgumentException("skill must not be blank");
        }
        return skill.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("_+", "_");
    }
}
