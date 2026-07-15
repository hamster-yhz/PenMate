package com.penmate.backend.application.agent.prompt;

import com.penmate.backend.application.agent.context.ContextPackage;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Composes execution prompt plan from profiled task, already-built context and modular skill catalog.
 */
@Component
public class PromptComposer {

    private final SystemPromptProvider systemPromptProvider;
    private final SkillPromptRegistry skillPromptRegistry;

    public PromptComposer(SystemPromptProvider systemPromptProvider,
                          SkillPromptRegistry skillPromptRegistry) {
        this.systemPromptProvider = systemPromptProvider;
        this.skillPromptRegistry = skillPromptRegistry;
    }

    public PromptPlan compose(TaskProfile taskProfile,
                              ContextPackage contextPackage,
                              String userRequest) {
        String finalProfile = normalizeProfile(taskProfile == null ? null : taskProfile.executionProfile());
        ContextPackage normalizedContext = Objects.requireNonNull(contextPackage, "contextPackage");

        SystemPromptBundle executionBundle = systemPromptProvider.loadBundle("execution", finalProfile);
        List<PromptModulePlan> modules = new ArrayList<>();
        List<String> previewSections = new ArrayList<>();

        modules.add(new PromptModulePlan(
                "execution:" + finalProfile,
                joinDocumentPaths(executionBundle == null ? null : executionBundle.documents()),
                true,
                "Execution base module for task profile=" + finalProfile
        ));
        previewSections.add(normalize(executionBundle == null ? null : executionBundle.assembledPrompt()));

        List<String> profileSkills = taskProfile == null || taskProfile.skills() == null ? List.of() : taskProfile.skills();
        List<SkillCatalogItem> availableSkills = skillPromptRegistry.listAvailableSkills().stream()
                .map(this::normalizeSkillCatalogItem)
                .filter(skill -> !skill.name().isEmpty())
                .toList();
        modules.add(new PromptModulePlan(
                "skill-catalog",
                "skill-catalog:" + availableSkills.stream()
                        .map(SkillCatalogItem::name)
                        .collect(Collectors.joining(",")),
                true,
                "Progressively disclosed skill catalog; full content is loaded through skill_load"
        ));
        previewSections.add(buildSkillCatalogPreview(availableSkills));

        modules.add(new PromptModulePlan(
                "context-package",
                describeContext(normalizedContext),
                true,
                "Consumes pre-built context package only"
        ));
        previewSections.add(buildContextPreview(normalizedContext));

        return new PromptPlan(
                modules,
                profileSkills,
                finalProfile,
                previewSections.stream()
                        .filter(section -> !section.isBlank())
                        .collect(Collectors.joining("\n\n"))
        );
    }

    private String buildContextPreview(ContextPackage contextPackage) {
        StringJoiner joiner = new StringJoiner("\n");
        contextPackage.storyBibleEntries().forEach(joiner::add);
        contextPackage.conflicts().forEach(joiner::add);
        contextPackage.missingContextFlags().forEach(joiner::add);
        return joiner.toString().trim();
    }

    private String describeContext(ContextPackage contextPackage) {
        return "context-package:sources=" + contextPackage.sources().size()
                + "/storyBibleEntries=" + contextPackage.storyBibleEntries().size()
                + "/ragRefs=" + contextPackage.ragRefs().size()
                + "/conflicts=" + contextPackage.conflicts().size()
                + "/missing=" + contextPackage.missingContextFlags().size();
    }

    private String buildSkillCatalogPreview(List<SkillCatalogItem> availableSkills) {
        if (availableSkills == null || availableSkills.isEmpty()) {
            return """
                    Available skills:
                    - none

                    Skill details are progressively disclosed. Use tool skill_load with {"skill":"<skill>"} only when full instructions are needed.
                    """.trim();
        }
        String skills = availableSkills.stream()
                .map(skill -> "- " + skill.name() + ": " + skill.description())
                .collect(Collectors.joining("\n"));
        return """
                Available skills:
                %s

                Skill details are progressively disclosed. Use tool skill_load with {"skill":"<skill>"} only when full instructions are needed.
                """.formatted(skills).trim();
    }

    private SkillCatalogItem normalizeSkillCatalogItem(SkillCatalogItem item) {
        if (item == null) {
            return new SkillCatalogItem("", "");
        }
        return new SkillCatalogItem(normalize(item.name()), normalize(item.description()));
    }

    private String joinDocumentPaths(List<SystemPromptDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }
        return documents.stream()
                .map(SystemPromptDocument::path)
                .map(this::normalize)
                .filter(path -> !path.isEmpty())
                .collect(Collectors.joining(","));
    }

    private String normalizeProfile(String profile) {
        String normalized = normalize(profile);
        return normalized.isEmpty() ? "default" : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
