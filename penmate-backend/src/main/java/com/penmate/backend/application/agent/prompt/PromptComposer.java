package com.penmate.backend.application.agent.prompt;

import com.penmate.backend.application.agent.context.ContextPackage;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import com.penmate.backend.application.agent.tool.selection.AgentToolSelectionPolicy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Composes execution prompt plan from profiled task, already-built context and modular skill catalog.
 */
@Component
public class PromptComposer {

    private final SystemPromptProvider systemPromptProvider;
    private final SkillPromptRegistry skillPromptRegistry;
    private final AgentToolSelectionPolicy toolSelectionPolicy;
    private final PromptContextRenderer contextRenderer;

    public PromptComposer(SystemPromptProvider systemPromptProvider,
                          SkillPromptRegistry skillPromptRegistry,
                          AgentToolSelectionPolicy toolSelectionPolicy,
                          PromptContextRenderer contextRenderer) {
        this.systemPromptProvider = systemPromptProvider;
        this.skillPromptRegistry = skillPromptRegistry;
        this.toolSelectionPolicy = toolSelectionPolicy;
        this.contextRenderer = contextRenderer;
    }

    public PromptPlan compose(TaskProfile taskProfile,
                              ContextPackage contextPackage,
                              String userRequest) {
        String finalProfile = normalizeProfile(taskProfile == null ? null : taskProfile.executionProfile());
        ContextPackage normalizedContext = Objects.requireNonNull(contextPackage, "contextPackage");

        SystemPromptBundle executionBundle = systemPromptProvider.loadBundle("execution", finalProfile);
        List<PromptModulePlan> modules = new ArrayList<>();
        List<String> stableSections = new ArrayList<>();
        List<String> dynamicSections = new ArrayList<>();

        modules.add(new PromptModulePlan(
                "execution:" + finalProfile,
                joinDocumentPaths(executionBundle == null ? null : executionBundle.documents()),
                true,
                "Execution base module for task profile=" + finalProfile
        ));
        stableSections.add(normalize(executionBundle == null ? null : executionBundle.assembledPrompt()));

        var registeredTools = toolSelectionPolicy.select(taskProfile);
        var toolSchemas = (registeredTools == null ? List.<com.penmate.backend.application.agent.llm.AgentLlmToolSchema>of() : registeredTools).stream()
                .sorted(java.util.Comparator.comparing(schema -> normalize(schema.toolCode())))
                .toList();
        modules.add(new PromptModulePlan(
                "tool-catalog",
                "tool-catalog:" + toolSchemas.stream().map(schema -> normalize(schema.toolCode())).collect(Collectors.joining(",")),
                true,
                "Stable deterministically ordered tool schemas"
        ));
        stableSections.add(buildToolCatalogPreview(toolSchemas));

        List<SkillCatalogItem> availableSkills = skillPromptRegistry.listAvailableSkills().stream()
                .map(this::normalizeSkillCatalogItem)
                .filter(skill -> !skill.name().isEmpty())
                .sorted(java.util.Comparator.comparing(SkillCatalogItem::name))
                .toList();
        modules.add(new PromptModulePlan(
                "skill-catalog",
                "skill-catalog:" + availableSkills.stream()
                        .map(SkillCatalogItem::name)
                        .collect(Collectors.joining(",")),
                true,
                "Progressively disclosed skill catalog; full content is loaded through skill_load"
        ));
        stableSections.add(buildSkillCatalogPreview(availableSkills));

        modules.add(new PromptModulePlan(
                "context-epoch-core",
                "context-epoch-core:entries=" + normalizedContext.coreStoryBibleEntries().size(),
                true,
                "Immutable Context Epoch core Story Bible"
        ));
        stableSections.add(contextRenderer.renderEpochCore(normalizedContext));

        modules.add(new PromptModulePlan(
                "context-package",
                describeContext(normalizedContext),
                false,
                "Dynamic history, Working Set and selected Story Bible context"
        ));
        dynamicSections.add(contextRenderer.renderRunContext(normalizedContext));

        String stablePrefix = stableSections.stream().filter(section -> !section.isBlank()).collect(Collectors.joining("\n\n"));
        String dynamicContext = dynamicSections.stream().filter(section -> !section.isBlank()).collect(Collectors.joining("\n\n"));

        return new PromptPlan(
                modules,
                toolSchemas,
                finalProfile,
                stablePrefix,
                dynamicContext,
                java.util.stream.Stream.of(stablePrefix, dynamicContext)
                        .filter(section -> !section.isBlank()).collect(Collectors.joining("\n\n"))
        );
    }

    private String buildToolCatalogPreview(List<com.penmate.backend.application.agent.llm.AgentLlmToolSchema> schemas) {
        if (schemas.isEmpty()) return "Available tools:\n- none";
        return "Available tools:\n" + schemas.stream()
                .map(schema -> "- " + normalize(schema.toolCode()) + ": " + normalize(schema.description()))
                .collect(Collectors.joining("\n"));
    }

    private String describeContext(ContextPackage contextPackage) {
        return "context-package:sources=" + contextPackage.sources().size()
                + "/storyBibleEntries=" + contextPackage.storyBibleEntries().size()
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
            return new SkillCatalogItem("", "", "");
        }
        return new SkillCatalogItem(
                normalize(item.name()),
                normalize(item.description()),
                normalize(item.contentHash())
        );
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
