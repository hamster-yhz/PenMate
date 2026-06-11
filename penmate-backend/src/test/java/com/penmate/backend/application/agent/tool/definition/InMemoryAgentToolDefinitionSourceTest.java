package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryAgentToolDefinitionSourceTest {

    @Test
    void getRequiredReturnsDescriptorDeclaredByToolDefinition() {
        InMemoryAgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                new TestToolDefinition(
                        "context_enhancer",
                        "Context Enhancer",
                        true,
                        "Enhance context",
                        "{\"type\":\"object\"}",
                        new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
                )
        ));

        AgentToolDescriptor descriptor = source.getRequired("context_enhancer");

        assertThat(descriptor.toolCode()).isEqualTo("context_enhancer");
        assertThat(descriptor.presentation().displayName()).isEqualTo("Context Enhancer");
    }

    @Test
    void rejectsDuplicatedToolDefinitions() {
        TestToolDefinition first = new TestToolDefinition(
                "book_crud",
                "Book CRUD",
                true,
                "Book CRUD",
                "{\"type\":\"object\"}",
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
        );
        TestToolDefinition duplicated = new TestToolDefinition(
                "book_crud",
                "Book CRUD Duplicate",
                true,
                "Book CRUD Duplicate",
                "{\"type\":\"object\"}",
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
        );

        assertThatThrownBy(() -> new InMemoryAgentToolDefinitionSource(List.of(first, duplicated)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicated tool definition: book_crud");
    }

    @Test
    void listLlmSchemasExposesRedisTodoCrudToolDefinition() {
        InMemoryAgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                new TodoCrudToolDefinition()
        ));

        AgentToolDescriptor descriptor = source.getRequired("todo_crud");
        Map<String, AgentLlmToolSchema> schemasByToolCode = source.listLlmSchemas().stream()
                .collect(Collectors.toMap(AgentLlmToolSchema::toolCode, schema -> schema));

        assertThat(descriptor.presentation().displayName()).isEqualTo("Todo CRUD");
        assertThat(schemasByToolCode).containsKey("todo_crud");
        assertThat(schemasByToolCode.get("todo_crud").description()).contains("Redis-backed session Todo");
        assertThat(schemasByToolCode.get("todo_crud").parametersJsonSchema())
                .contains("\"reorder\"")
                .contains("\"status\"")
                .doesNotContain("todo_planner");
    }

    @Test
    void missingDescriptorThrowsStableError() {
        InMemoryAgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                new TodoCrudToolDefinition()
        ));

        assertThatThrownBy(() -> source.getRequired("missing_tool"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tool descriptor not found: missing_tool");
    }

    private record TestToolDefinition(
            String toolCode,
            String displayName,
            boolean exposedToLlm,
            String llmDescription,
            String parametersJsonSchema,
            ToolGovernancePolicy governancePolicy
    ) implements AgentToolDefinition {

        @Override
        public AgentToolDescriptor descriptor() {
            return new AgentToolDescriptor(
                    toolCode,
                    new ToolPresentation(displayName),
                    new ToolExposure(exposedToLlm, llmDescription, parametersJsonSchema),
                    governancePolicy
            );
        }
    }
}
