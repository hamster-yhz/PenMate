package com.penmate.backend.application.agent.prompt;

import com.penmate.backend.application.agent.context.ContextPackage;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.definition.ToolExposure;
import com.penmate.backend.application.agent.tool.definition.ToolGovernancePolicy;
import com.penmate.backend.application.agent.tool.definition.ToolLifecycleStatus;
import com.penmate.backend.application.agent.tool.definition.ToolPresentation;
import com.penmate.backend.application.agent.tool.selection.AgentToolSelectionPolicy;
import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptComposerTest {

    private final SystemPromptProvider systemPromptProvider = mock(SystemPromptProvider.class);
    private final SkillPromptRegistry skillPromptRegistry = mock(SkillPromptRegistry.class);
    private final AgentToolDefinitionSource toolDefinitionSource = mock(AgentToolDefinitionSource.class);
    private final PromptComposer promptComposer = new PromptComposer(
            systemPromptProvider,
            skillPromptRegistry,
            new AgentToolSelectionPolicy(toolDefinitionSource),
            new PromptContextRenderer(new StructuredPromptBlockFormatter()));

    @Test
    void snapshots_selected_tools_without_repeating_json_schema_in_the_prompt() {
        stubExecutionBundle("default", "execution base");
        when(skillPromptRegistry.listAvailableSkills()).thenReturn(List.of());
        when(toolDefinitionSource.listAll()).thenReturn(List.of(new AgentToolDescriptor(
                "rag_query",
                new ToolPresentation("RAG query"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE, "Search manuscript context", "{\"type\":\"object\"}"),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 0, java.util.Map.of())
        )));

        PromptPlan plan = promptComposer.compose(
                emptyContextPackage(), "Find Mira");

        assertThat(plan.toolSchemas()).extracting(schema -> schema.toolCode()).containsExactly("rag_query");
        assertThat(plan.stablePrefix())
                .contains("- rag_query: Search manuscript context")
                .doesNotContain("parameters:")
                .doesNotContain("{\"type\":\"object\"}");
    }

    @Test
    void should_keep_user_request_out_of_prompt_preview_and_expose_skill_catalog_only() {
        stubExecutionBundle("default", "execution base");
        when(skillPromptRegistry.listAvailableSkills()).thenReturn(List.of(
                new SkillCatalogItem("writer", "Write prose and scenes"),
                new SkillCatalogItem("planner", "Plan writing tasks"),
                new SkillCatalogItem("checker", "Check continuity and constraints")
        ));

        PromptPlan promptPlan = promptComposer.compose(
                new ContextPackage(
                        List.of("style-snapshot"),
                        List.of(),
                        List.of(),
                        List.of(),
                        "{\"person\":\"first\"}",
                        "chapter:12"
                ),
                "Please write in first person."
        );

        assertThat(promptPlan.assembledPromptPreview())
                .contains("execution base")
                .contains("Available skills")
                .contains("- writer: Write prose and scenes")
                .contains("- planner: Plan writing tasks")
                .contains("- checker: Check continuity and constraints")
                .contains("skill_load")
                .doesNotContain("skill_prompt_read")
                .doesNotContain("Please write in first person.");
        verify(skillPromptRegistry, never()).load("writer");
        assertThat(promptPlan.modules())
                .extracting(PromptModulePlan::moduleKey)
                .containsExactly("execution", "tool-catalog", "skill-catalog", "context-epoch-core", "context-package");
        assertThat(promptPlan.modules())
                .extracting(PromptModulePlan::source)
                .containsExactly(
                        "prompts/agent/system/execution/default/00-base-role.md",
                        "tool-catalog:",
                        "skill-catalog:checker,planner,writer",
                        "context-epoch-core:entries=0",
                        "context-package:sources=1/storyBibleEntries=0/conflicts=0/missing=0"
                );
    }

    @Test
    void should_expose_catalog_without_loading_skill_bodies() {
        stubExecutionBundle("default", "execution base");
        when(skillPromptRegistry.listAvailableSkills()).thenReturn(List.of(
                new SkillCatalogItem("writer", "Write prose and scenes"),
                new SkillCatalogItem("planner", "Plan writing tasks"),
                new SkillCatalogItem("checker", "Check continuity and constraints"),
                new SkillCatalogItem("story-bible", "Read relevant story bible facts")
        ));

        PromptPlan promptPlan = promptComposer.compose(
                emptyContextPackage(),
                "Generate a plot outline."
        );

        assertThat(promptPlan.assembledPromptPreview())
                .contains("Available skills")
                .contains("- planner: Plan writing tasks")
                .contains("- checker: Check continuity and constraints")
                .contains("- story-bible: Read relevant story bible facts")
                .contains("skill_load");
        verify(skillPromptRegistry, never()).load("planner");
        verify(skillPromptRegistry, never()).load("checker");
        assertThat(promptPlan.modules())
                .extracting(PromptModulePlan::moduleKey)
                .containsExactly("execution", "tool-catalog", "skill-catalog", "context-epoch-core", "context-package");
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
                "{\"tone\":\"restrained\"}",
                "chapter:21"
        );

        PromptPlan promptPlan = promptComposer.compose(
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
    void should_render_escaped_typed_context_blocks_without_duplicate_story_bible_entries() {
        stubExecutionBundle("default", "default base");
        when(skillPromptRegistry.listAvailableSkills()).thenReturn(List.of());
        ContextPackage contextPackage = new ContextPackage(
                List.of("story-bible"),
                List.of("timeline missing"),
                List.of("age conflict"),
                List.of("unused aggregate"),
                List.of("core <canon>"),
                List.of("shared node", "working node"),
                List.of("shared node", "selected node"),
                "style <restrained>",
                "chapter:21"
        );

        PromptPlan promptPlan = promptComposer.compose(
                contextPackage, "Continue");

        assertThat(promptPlan.stablePrefix())
                .contains("<context type=\"story_bible\" scope=\"epoch_core\">")
                .contains("core &lt;canon&gt;");
        assertThat(promptPlan.dynamicContext())
                .contains("<context type=\"style\">")
                .contains("style &lt;restrained&gt;")
                .contains("<context type=\"chapter_scope\">")
                .containsOnlyOnce("shared node");
    }

    @Test
    void should_include_module_sources_for_logging_and_snapshot() {
        when(systemPromptProvider.loadBundle("execution")).thenReturn(new SystemPromptBundle(
                "execution",
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
        when(skillPromptRegistry.listAvailableSkills()).thenReturn(List.of(
                new SkillCatalogItem("editor", "Polish and revise existing prose")
        ));

        PromptPlan promptPlan = promptComposer.compose(
                emptyContextPackage(),
                "Polish paragraph"
        );

        assertThat(promptPlan.modules())
                .extracting(PromptModulePlan::moduleKey)
                .containsExactly("execution", "tool-catalog", "skill-catalog", "context-epoch-core", "context-package");
        assertThat(promptPlan.modules())
                .extracting(PromptModulePlan::source)
                .containsExactly(
                        "prompts/agent/system/execution/default/00-base-role.md,prompts/agent/system/execution/default/10-writing-rules.md",
                        "tool-catalog:",
                        "skill-catalog:editor",
                        "context-epoch-core:entries=0",
                        "context-package:sources=0/storyBibleEntries=0/conflicts=0/missing=0"
                );
    }

    private void stubExecutionBundle(String profile, String content) {
        when(systemPromptProvider.loadBundle("execution")).thenReturn(new SystemPromptBundle(
                "execution",
                List.of(new SystemPromptDocument(
                        "00-base-role.md",
                        "prompts/agent/system/execution/" + profile + "/00-base-role.md",
                        content
                )),
                content
        ));
    }

    private ContextPackage emptyContextPackage() {
        return new ContextPackage(List.of(), List.of(), List.of(), List.of(), "", "");
    }
}
