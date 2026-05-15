package com.penmate.backend.infrastructure.agent.prompt;

import com.penmate.backend.application.agent.prompt.PromptComposer;
import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import com.penmate.backend.application.agent.prompt.SystemPromptDocument;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathSkillPromptRegistryTest {

    @Test
    void should_register_skill_prompt_registry_for_prompt_composer_and_resolve_runtime_skill_aliases() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(PromptComponentScanConfig.class);
            context.refresh();

            assertThat(context.getBean(PromptComposer.class)).isNotNull();

            SkillPromptRegistry registry = context.getBean(SkillPromptRegistry.class);
            SystemPromptDocument writerPrompt = registry.load("writer");
            SystemPromptDocument sceneWriterPrompt = registry.load("scene-writer");
            SystemPromptDocument continuityCheckerPrompt = registry.load("continuity_checker");
            SystemPromptDocument storyBibleQueryPrompt = registry.load("story_bible_query");
            SystemPromptDocument storyBibleGuardPrompt = registry.load("story-bible-guard");

            assertThat(writerPrompt.path()).isEqualTo("prompts/agent/system/skills/writer/00-base-role.md");
            assertThat(writerPrompt.content()).isNotBlank();
            assertThat(sceneWriterPrompt.path()).isEqualTo("prompts/agent/system/skills/writer/00-base-role.md");
            assertThat(sceneWriterPrompt.content()).isNotBlank();
            assertThat(continuityCheckerPrompt.path()).isEqualTo("prompts/agent/system/skills/checker/00-base-role.md");
            assertThat(continuityCheckerPrompt.content()).isNotBlank();
            assertThat(storyBibleQueryPrompt.path()).isEqualTo("prompts/agent/system/skills/story-bible/00-base-role.md");
            assertThat(storyBibleQueryPrompt.content()).isNotBlank();
            assertThat(storyBibleGuardPrompt.path()).isEqualTo("prompts/agent/system/skills/story-bible/00-base-role.md");
            assertThat(storyBibleGuardPrompt.content()).isNotBlank();
        }
    }

    @Test
    void should_fail_fast_for_unknown_skill_alias_instead_of_falling_back_to_generic_directory() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(PromptComponentScanConfig.class);
            context.refresh();

            SkillPromptRegistry registry = context.getBean(SkillPromptRegistry.class);
            assertThatThrownBy(() -> registry.load("mystery-planner"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported skill prompt: mystery-planner");
        }
    }

    @Configuration
    @ComponentScan(basePackageClasses = {
            PromptComposer.class,
            ClasspathMarkdownSystemPromptProvider.class
    })
    static class PromptComponentScanConfig {
    }
}
