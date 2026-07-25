package com.penmate.backend.infrastructure.agent.prompt;

import com.penmate.backend.application.agent.prompt.PromptComposer;
import com.penmate.backend.application.agent.prompt.LoadedSkill;
import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.InMemoryAgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.selection.AgentToolSelectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URLConnection;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathSkillPromptRegistryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void should_register_canonical_skill_prompts_for_prompt_composer() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(PromptComponentScanConfig.class);
            context.refresh();

            assertThat(context.getBean(PromptComposer.class)).isNotNull();

            SkillPromptRegistry registry = context.getBean(SkillPromptRegistry.class);
            LoadedSkill writerPrompt = registry.load("scene-writing");
            LoadedSkill checkerPrompt = registry.load("novel-review");
            LoadedSkill storyBiblePrompt = registry.load("canon-maintenance");

            assertThat(writerPrompt.instructions().path()).isEqualTo("prompts/agent/system/skills/scene-writing/SKILL.md");
            assertThat(writerPrompt.instructions().content()).isNotBlank();
            assertThat(checkerPrompt.descriptor().name()).isEqualTo("novel-review");
            assertThat(storyBiblePrompt.descriptor().name()).isEqualTo("canon-maintenance");
        }
    }

    @Test
    void should_reject_unknown_skill() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(PromptComponentScanConfig.class);
            context.refresh();

            SkillPromptRegistry registry = context.getBean(SkillPromptRegistry.class);
            assertThatThrownBy(() -> registry.load("mystery-planner"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Skill not found: mystery-planner");
        }
    }

    @Test
    void should_discover_canonical_skills_with_content_hashes() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(PromptComponentScanConfig.class);
            context.refresh();

            SkillPromptRegistry registry = context.getBean(SkillPromptRegistry.class);

            assertThat(registry.listAvailableSkills())
                    .extracting("name")
                    .containsExactly("canon-maintenance", "developmental-editing", "line-editing", "novel-review", "scene-writing", "story-planning")
                    .doesNotContain("checker", "editor", "planner", "story-bible", "writer");
            assertThat(registry.listAvailableSkills())
                    .extracting("description")
                    .allSatisfy(description -> assertThat((String) description).isNotBlank());
            assertThat(registry.listAvailableSkills())
                    .extracting("contentHash")
                    .allSatisfy(hash -> assertThat((String) hash).matches("[0-9a-f]{64}"));
        }
    }

    @Configuration
    @ComponentScan(basePackageClasses = {
            PromptComposer.class,
            ClasspathMarkdownSystemPromptProvider.class
    })
    static class PromptComponentScanConfig {

        @Bean
        AgentToolDefinitionSource agentToolDefinitionSource() {
            return new InMemoryAgentToolDefinitionSource(java.util.List.of());
        }

        @Bean
        AgentToolSelectionPolicy agentToolSelectionPolicy(AgentToolDefinitionSource definitions) {
            return new AgentToolSelectionPolicy(definitions);
        }
    }

    @Test
    void should_discover_skill_packages_inside_jar_files() throws IOException {
        Path jar = tempDirectory.resolve("skills.jar");
        String skillPath = "prompts/agent/system/skills/jar-writer/SKILL.md";
        String skill = """
                ---
                name: jar-writer
                description: Write content from a packaged skill.
                ---

                Follow the packaged writing instructions.
                """;
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addDirectory(output, "prompts/");
            addDirectory(output, "prompts/agent/");
            addDirectory(output, "prompts/agent/system/");
            addDirectory(output, "prompts/agent/system/skills/");
            addDirectory(output, "prompts/agent/system/skills/jar-writer/");
            output.putNextEntry(new JarEntry(skillPath));
            output.write(skill.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        boolean previousJarCaching = URLConnection.getDefaultUseCaches("jar");
        URLConnection.setDefaultUseCaches("jar", false);
        try {
            try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, null)) {
                var registry = new ClasspathSkillPromptRegistry(
                        new PathMatchingResourcePatternResolver(classLoader));

                assertThat(registry.listAvailableSkills()).extracting("name").containsExactly("jar-writer");
                assertThat(registry.load("jar-writer").instructions().content())
                        .isEqualTo("Follow the packaged writing instructions.");
            }
        } finally {
            URLConnection.setDefaultUseCaches("jar", previousJarCaching);
        }
    }

    private void addDirectory(JarOutputStream output, String path) throws IOException {
        output.putNextEntry(new JarEntry(path));
        output.closeEntry();
    }
}
