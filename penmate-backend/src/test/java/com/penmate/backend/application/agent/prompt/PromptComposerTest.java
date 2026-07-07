package com.penmate.backend.application.agent.prompt;

import com.penmate.backend.application.agent.context.ContextPackage;
import com.penmate.backend.application.agent.orchestration.profile.TaskIntentTag;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptComposerTest {

    private final SystemPromptProvider systemPromptProvider = mock(SystemPromptProvider.class);
    private final SkillPromptRegistry skillPromptRegistry = mock(SkillPromptRegistry.class);
    private final PromptComposer promptComposer = new PromptComposer(systemPromptProvider, skillPromptRegistry);

    @Test
    void should_keep_user_request_out_of_prompt_preview_and_expose_skill_catalog_only() {
        stubExecutionBundle("default", "execution base");
        when(skillPromptRegistry.listAvailableSkills()).thenReturn(List.of("writer", "planner", "checker"));

        PromptPlan promptPlan = promptComposer.compose(
                new TaskProfile(
                        List.of(TaskIntentTag.DRAFT_GENERATION),
                        "default",
                        List.of("writer"),
                        List.of(),
                        List.of("keep explicit user perspective"),
                        "draft output",
                        false,
                        false,
                        false,
                        "draft request"
                ),
                new ContextPackage(
                        List.of("style-snapshot"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "{\"person\":\"first\"}",
                        "chapter:12"
                ),
                "Please write in first person."
        );

        assertThat(promptPlan.finalProfile()).isEqualTo("default");
        assertThat(promptPlan.skills()).containsExactly("writer");
        assertThat(promptPlan.assembledPromptPreview())
                .contains("execution base")
                .contains("Available skills")
                .contains("writer")
                .contains("planner")
                .contains("checker")
                .contains("skill_prompt_read")
                .doesNotContain("Please write in first person.");
        verify(skillPromptRegistry, never()).load("writer");
        assertThat(promptPlan.modules())
                .extracting(PromptModulePlan::moduleKey)
                .containsExactly("execution:default", "skill-catalog", "context-package");
        assertThat(promptPlan.modules())
                .extracting(PromptModulePlan::source)
                .containsExactly(
                        "prompts/agent/system/execution/default/00-base-role.md",
                        "skill-catalog:writer,planner,checker",
                        "context-package:sources=1/storyBibleEntries=0/ragRefs=0/conflicts=0/missing=0"
                );
    }

    @Test
    void should_not_load_task_profile_skills_as_prompt_modules() {
        stubExecutionBundle("default", "execution base");
        when(skillPromptRegistry.listAvailableSkills()).thenReturn(List.of("writer", "planner", "checker", "story_bible_query"));

        PromptPlan promptPlan = promptComposer.compose(
                profileFor("default", List.of("planner", "checker")),
                emptyContextPackage(),
                "Generate a plot outline."
        );

        assertThat(promptPlan.skills()).containsExactly("planner", "checker");
        assertThat(promptPlan.assembledPromptPreview())
                .contains("Available skills")
                .contains("planner")
                .contains("checker")
                .contains("story_bible_query")
                .contains("skill_prompt_read");
        verify(skillPromptRegistry, never()).load("planner");
        verify(skillPromptRegistry, never()).load("checker");
        assertThat(promptPlan.modules())
                .extracting(PromptModulePlan::moduleKey)
                .containsExactly("execution:default", "skill-catalog", "context-package");
    }

    @Test
    void should_align_execution_bundle_with_world_build_rewrite_and_default_profiles() {
        stubExecutionBundle("world-build", "world build base");
        stubExecutionBundle("rewrite", "rewrite base");
        stubExecutionBundle("default", "default base");
        when(skillPromptRegistry.listAvailableSkills()).thenReturn(List.of());

        PromptPlan worldBuildPlan = promptComposer.compose(profileFor("world-build", List.of()), emptyContextPackage(), "Complete setting");
        PromptPlan rewritePlan = promptComposer.compose(profileFor("rewrite", List.of()), emptyContextPackage(), "Rewrite copy");
        PromptPlan defaultPlan = promptComposer.compose(profileFor("default", List.of()), emptyContextPackage(), "Continue draft");

        assertThat(worldBuildPlan.finalProfile()).isEqualTo("world-build");
        assertThat(worldBuildPlan.assembledPromptPreview()).contains("world build base");
        assertThat(rewritePlan.finalProfile()).isEqualTo("rewrite");
        assertThat(rewritePlan.assembledPromptPreview()).contains("rewrite base");
        assertThat(defaultPlan.finalProfile()).isEqualTo("default");
        assertThat(defaultPlan.assembledPromptPreview()).contains("default base");
    }

    @Test
    void should_only_consume_built_context_package_without_querying_story_bible_directly() {
        stubExecutionBundle("default", "default base");
        when(skillPromptRegistry.listAvailableSkills()).thenReturn(List.of());

        ContextPackage contextPackage = new ContextPackage(
                List.of("story-bible", "style-snapshot"),
                List.of("rag-missing"),
                List.of("story bible conflict: character age"),
                List.of("character age: 17 (canon)"),
                List.of(),
                "{\"tone\":\"restrained\"}",
                "chapter:21"
        );

        PromptPlan promptPlan = promptComposer.compose(
                profileFor("default", List.of()),
                contextPackage,
                "Continue after checking continuity."
        );

        assertThat(promptPlan.assembledPromptPreview())
                .contains("default base")
                .contains("character age: 17 (canon)")
                .contains("story bible conflict: character age")
                .contains("rag-missing");
        verify(skillPromptRegistry).listAvailableSkills();
        verify(skillPromptRegistry, never()).load(anyString());
    }

    @Test
    void should_include_module_sources_for_logging_and_snapshot() {
        when(systemPromptProvider.loadBundle("execution", "default")).thenReturn(new SystemPromptBundle(
                "execution",
                "default",
                List.of(
                        new SystemPromptDocument(
                                "00-base-role.md",
                                "prompts/agent/system/execution/default/00-base-role.md",
                                "default base"
                        ),
                        new SystemPromptDocument(
                                "10-writing-rules.md",
                                "prompts/agent/system/execution/default/10-writing-rules.md",
                                "writing rules"
                        )
                ),
                "default base\n\nwriting rules"
        ));
        when(skillPromptRegistry.listAvailableSkills()).thenReturn(List.of("editor"));

        PromptPlan promptPlan = promptComposer.compose(
                profileFor("default", List.of("editor")),
                emptyContextPackage(),
                "Polish paragraph"
        );

        assertThat(promptPlan.modules()).containsExactly(
                new PromptModulePlan(
                        "execution:default",
                        "prompts/agent/system/execution/default/00-base-role.md,prompts/agent/system/execution/default/10-writing-rules.md",
                        true,
                        "执行基座模块，匹配 task profile=default"
                ),
                new PromptModulePlan(
                        "skill-catalog",
                        "skill-catalog:editor",
                        true,
                        "渐进式披露 skill 目录，正文通过 skill_prompt_read tool 按需读取"
                ),
                new PromptModulePlan(
                        "context-package",
                        "context-package:sources=0/storyBibleEntries=0/ragRefs=0/conflicts=0/missing=0",
                        true,
                        "仅消费已构建上下文结果，不直接查询 story bible"
                )
        );
    }

    @Test
    void should_fail_fast_when_context_package_is_null() {
        stubExecutionBundle("default", "default base");

        assertThatThrownBy(() -> promptComposer.compose(profileFor("default", List.of()), null, "Continue draft"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("contextPackage");
    }

    private void stubExecutionBundle(String profile, String content) {
        when(systemPromptProvider.loadBundle("execution", profile)).thenReturn(new SystemPromptBundle(
                "execution",
                profile,
                List.of(new SystemPromptDocument(
                        "00-base-role.md",
                        "prompts/agent/system/execution/" + profile + "/00-base-role.md",
                        content
                )),
                content
        ));
    }

    private TaskProfile profileFor(String executionProfile, List<String> skills) {
        return new TaskProfile(
                List.of(TaskIntentTag.DRAFT_GENERATION),
                executionProfile,
                skills,
                List.of(),
                List.of(),
                "draft output",
                false,
                true,
                false,
                "test profile"
        );
    }

    private ContextPackage emptyContextPackage() {
        return new ContextPackage(List.of(), List.of(), List.of(), List.of(), List.of(), "", "");
    }
}
