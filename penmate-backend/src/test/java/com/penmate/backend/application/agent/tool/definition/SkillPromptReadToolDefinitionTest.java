package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SkillPromptReadToolDefinitionTest {

    @Test
    void should_expose_skill_prompt_read_tool_to_llm() {
        InMemoryAgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                new SkillPromptReadToolDefinition()
        ));

        AgentToolDescriptor descriptor = source.getRequired("skill_prompt_read");
        AgentLlmToolSchema schema = source.listLlmSchemas().stream()
                .collect(Collectors.toMap(AgentLlmToolSchema::toolCode, item -> item))
                .get("skill_prompt_read");

        assertThat(descriptor.presentation().displayName()).isEqualTo("Skill Prompt Read");
        assertThat(schema).isNotNull();
        assertThat(schema.description()).contains("Read full skill prompt content");
        assertThat(schema.parametersJsonSchema())
                .contains("\"skill\"")
                .contains("\"required\"");
    }
}
