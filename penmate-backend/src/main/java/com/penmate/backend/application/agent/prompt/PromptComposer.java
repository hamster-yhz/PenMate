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
 * Composes execution prompt plan from profiled task, already-built context and modular skill prompts.
 * <p>
 * This composer does not query repositories or build context on its own. It only consumes the provided
 * {@link TaskProfile} and {@link ContextPackage}, producing a snapshot-safe {@link PromptPlan} for logging,
 * recovery and downstream message assembly.
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
                "执行基座模块，匹配 task profile=" + finalProfile
        ));
        previewSections.add(normalize(executionBundle == null ? null : executionBundle.assembledPrompt()));

        List<String> skills = taskProfile == null || taskProfile.skills() == null ? List.of() : taskProfile.skills();
        for (String skill : skills) {
            String normalizedSkill = normalize(skill);
            if (normalizedSkill.isEmpty()) {
                continue;
            }
            SystemPromptDocument skillDocument = skillPromptRegistry.load(normalizedSkill);
            modules.add(new PromptModulePlan(
                    "skill:" + normalizedSkill,
                    normalize(skillDocument == null ? null : skillDocument.path()),
                    true,
                    "根据 task profile skills 激活"
            ));
            previewSections.add(normalize(skillDocument == null ? null : skillDocument.content()));
        }

        modules.add(new PromptModulePlan(
                "context-package",
                describeContext(normalizedContext),
                true,
                "仅消费已构建上下文结果，不直接查询 story bible"
        ));
        previewSections.add(buildContextPreview(normalizedContext));

        return new PromptPlan(
                modules,
                skills,
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
