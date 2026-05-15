package com.penmate.backend.application.agent.prompt;

import com.penmate.backend.application.agent.context.ContextPackage;
import com.penmate.backend.application.agent.orchestration.profile.TaskIntentTag;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PromptComposerTest {

    private final SystemPromptProvider systemPromptProvider = mock(SystemPromptProvider.class);
    private final SkillPromptRegistry skillPromptRegistry = mock(SkillPromptRegistry.class);
    private final PromptComposer promptComposer = new PromptComposer(systemPromptProvider, skillPromptRegistry);

    @Test
    void should_keep_user_request_out_of_prompt_preview_so_explicit_user_instruction_overrides_skill_prompt() {
        when(systemPromptProvider.loadBundle("execution", "default")).thenReturn(new SystemPromptBundle(
                "execution",
                "default",
                List.of(
                        new SystemPromptDocument(
                                "00-base-role.md",
                                "prompts/agent/system/execution/default/00-base-role.md",
                                "你是执行阶段写作代理。"
                        )
                ),
                "你是执行阶段写作代理。"
        ));
        when(skillPromptRegistry.load("writer")).thenReturn(new SystemPromptDocument(
                "00-base-role.md",
                "prompts/agent/system/skills/writer/00-base-role.md",
                "默认使用第三人称叙事。"
        ));

        TaskProfile taskProfile = new TaskProfile(
                List.of(TaskIntentTag.DRAFT_GENERATION),
                "default",
                List.of("writer"),
                List.of(),
                List.of("保留用户明确指定的人称"),
                "输出正文续写",
                false,
                false,
                false,
                "用户要求正文续写"
        );

        PromptPlan promptPlan = promptComposer.compose(
                taskProfile,
                new ContextPackage(
                        List.of("style-snapshot"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "{\"person\":\"first\"}",
                        "chapter:12"
                ),
                "请用第一人称续写这一段。"
        );

        assertThat(promptPlan.finalProfile()).isEqualTo("default");
        assertThat(promptPlan.skills()).containsExactly("writer");
        assertThat(promptPlan.assembledPromptPreview())
                .contains("你是执行阶段写作代理。")
                .contains("默认使用第三人称叙事。")
                .doesNotContain("请用第一人称续写这一段");
        assertThat(promptPlan.modules())
                .extracting(PromptModulePlan::moduleKey)
                .containsExactly("execution:default", "skill:writer", "context-package");
        assertThat(promptPlan.modules())
                .extracting(PromptModulePlan::source)
                .containsExactly(
                        "prompts/agent/system/execution/default/00-base-role.md",
                        "prompts/agent/system/skills/writer/00-base-role.md",
                        "context-package:sources=1/storyBibleEntries=0/ragRefs=0/conflicts=0/missing=0"
                );
    }

    @Test
    void should_activate_distinct_skill_prompts_from_task_profile_skills() {
        when(systemPromptProvider.loadBundle("execution", "default")).thenReturn(new SystemPromptBundle(
                "execution",
                "default",
                List.of(),
                "执行基座"
        ));
        when(skillPromptRegistry.load("planner")).thenReturn(new SystemPromptDocument(
                "00-base-role.md",
                "prompts/agent/system/skills/planner/00-base-role.md",
                "先规划再写作。"
        ));
        when(skillPromptRegistry.load("checker")).thenReturn(new SystemPromptDocument(
                "00-base-role.md",
                "prompts/agent/system/skills/checker/00-base-role.md",
                "输出前检查一致性。"
        ));

        TaskProfile taskProfile = new TaskProfile(
                List.of(TaskIntentTag.DRAFT_GENERATION, TaskIntentTag.CONTINUITY_CHECK),
                "default",
                List.of("planner", "checker"),
                List.of(),
                List.of(),
                "先规划后输出正文",
                false,
                false,
                false,
                "需要规划和检查"
        );

        PromptPlan promptPlan = promptComposer.compose(taskProfile, emptyContextPackage(), "请生成剧情大纲");

        assertThat(promptPlan.skills()).containsExactly("planner", "checker");
        assertThat(promptPlan.assembledPromptPreview())
                .contains("执行基座")
                .contains("先规划再写作。")
                .contains("输出前检查一致性。");
        assertThat(promptPlan.modules())
                .extracting(PromptModulePlan::moduleKey)
                .containsExactly("execution:default", "skill:planner", "skill:checker", "context-package");
    }

    @Test
    void should_align_execution_bundle_with_world_build_rewrite_and_default_profiles() {
        when(systemPromptProvider.loadBundle("execution", "world-build")).thenReturn(new SystemPromptBundle(
                "execution",
                "world-build",
                List.of(),
                "世界观构建基座"
        ));
        when(systemPromptProvider.loadBundle("execution", "rewrite")).thenReturn(new SystemPromptBundle(
                "execution",
                "rewrite",
                List.of(),
                "改写基座"
        ));
        when(systemPromptProvider.loadBundle("execution", "default")).thenReturn(new SystemPromptBundle(
                "execution",
                "default",
                List.of(),
                "默认执行基座"
        ));

        PromptPlan worldBuildPlan = promptComposer.compose(profileFor("world-build", List.of()), emptyContextPackage(), "补全设定");
        PromptPlan rewritePlan = promptComposer.compose(profileFor("rewrite", List.of()), emptyContextPackage(), "改写文案");
        PromptPlan defaultPlan = promptComposer.compose(profileFor("default", List.of()), emptyContextPackage(), "续写正文");

        assertThat(worldBuildPlan.finalProfile()).isEqualTo("world-build");
        assertThat(worldBuildPlan.assembledPromptPreview()).contains("世界观构建基座");
        assertThat(rewritePlan.finalProfile()).isEqualTo("rewrite");
        assertThat(rewritePlan.assembledPromptPreview()).contains("改写基座");
        assertThat(defaultPlan.finalProfile()).isEqualTo("default");
        assertThat(defaultPlan.assembledPromptPreview()).contains("默认执行基座");
    }

    @Test
    void should_only_consume_built_context_package_without_querying_story_bible_directly() {
        when(systemPromptProvider.loadBundle("execution", "default")).thenReturn(new SystemPromptBundle(
                "execution",
                "default",
                List.of(),
                "默认执行基座"
        ));

        ContextPackage contextPackage = new ContextPackage(
                List.of("story-bible", "style-snapshot"),
                List.of("rag-missing"),
                List.of("设定冲突：角色年龄待确认"),
                List.of("角色年龄：17（canon）"),
                List.of(),
                "{\"tone\":\"克制\"}",
                "chapter:21"
        );

        PromptPlan promptPlan = promptComposer.compose(
                profileFor("default", List.of()),
                contextPackage,
                "核对设定后继续写作"
        );

        assertThat(promptPlan.assembledPromptPreview())
                .contains("默认执行基座")
                .contains("角色年龄：17（canon）")
                .contains("设定冲突：角色年龄待确认")
                .contains("rag-missing");
        verifyNoInteractions(skillPromptRegistry);
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
                                "默认执行基座"
                        ),
                        new SystemPromptDocument(
                                "10-writing-rules.md",
                                "prompts/agent/system/execution/default/10-writing-rules.md",
                                "写作规则"
                        )
                ),
                "默认执行基座\n\n写作规则"
        ));
        when(skillPromptRegistry.load("editor")).thenReturn(new SystemPromptDocument(
                "00-base-role.md",
                "prompts/agent/system/skills/editor/00-base-role.md",
                "进行句式润色。"
        ));

        PromptPlan promptPlan = promptComposer.compose(
                profileFor("default", List.of("editor")),
                emptyContextPackage(),
                "润色段落"
        );

        assertThat(promptPlan.modules()).containsExactly(
                new PromptModulePlan(
                        "execution:default",
                        "prompts/agent/system/execution/default/00-base-role.md,prompts/agent/system/execution/default/10-writing-rules.md",
                        true,
                        "执行基座模块，匹配 task profile=default"
                ),
                new PromptModulePlan(
                        "skill:editor",
                        "prompts/agent/system/skills/editor/00-base-role.md",
                        true,
                        "根据 task profile skills 激活"
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
        when(systemPromptProvider.loadBundle("execution", "default")).thenReturn(new SystemPromptBundle(
                "execution",
                "default",
                List.of(),
                "默认执行基座"
        ));

        assertThatThrownBy(() -> promptComposer.compose(profileFor("default", List.of()), null, "续写正文"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("contextPackage");
    }

    private TaskProfile profileFor(String executionProfile, List<String> skills) {
        return new TaskProfile(
                List.of(TaskIntentTag.DRAFT_GENERATION),
                executionProfile,
                skills,
                List.of(),
                List.of(),
                "输出正文",
                false,
                true,
                false,
                "测试 profile"
        );
    }

    private ContextPackage emptyContextPackage() {
        return new ContextPackage(List.of(), List.of(), List.of(), List.of(), List.of(), "", "");
    }
}
