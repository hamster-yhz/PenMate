package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.prompt.SkillCatalogItem;
import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillLoadToolDefinitionTest {

    private final SkillPromptRegistry skillPromptRegistry = mock(SkillPromptRegistry.class);

    @Test
    void should_expose_skill_load_tool_to_llm_with_catalog_enum() {
        when(skillPromptRegistry.listAvailableSkills()).thenReturn(List.of(
                new SkillCatalogItem("writer", "Write prose and scenes"),
                new SkillCatalogItem("planner", "Plan writing tasks"),
                new SkillCatalogItem("checker", "Check continuity and constraints")
        ));
        InMemoryAgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                new SkillLoadToolDefinition(skillPromptRegistry)
        ));

        AgentToolDescriptor descriptor = source.getRequired("skill_load");
        AgentLlmToolSchema schema = source.listLlmSchemas().stream()
                .collect(Collectors.toMap(AgentLlmToolSchema::toolCode, item -> item))
                .get("skill_load");

        assertThat(descriptor.presentation().displayName()).isEqualTo("Skill Load");
        assertThat(schema).isNotNull();
        assertThat(schema.description()).contains("Load full skill instructions");
        assertThat(schema.parametersJsonSchema())
                .contains("\"skill\"")
                .contains("\"required\"")
                .contains("\"enum\"")
                .contains("\"writer\"")
                .contains("\"planner\"")
                .contains("\"checker\"");
    }
}
