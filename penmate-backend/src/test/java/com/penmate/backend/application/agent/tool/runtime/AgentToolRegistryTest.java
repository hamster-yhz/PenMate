package com.penmate.backend.application.agent.tool.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.prompt.SkillCatalogItem;
import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import com.penmate.backend.application.agent.tool.definition.BookCrudToolDefinition;
import com.penmate.backend.application.agent.tool.definition.ChapterEditToolDefinition;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.definition.InMemoryAgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.QualityReviewToolDefinition;
import com.penmate.backend.application.agent.tool.definition.RagQueryToolDefinition;
import com.penmate.backend.application.agent.tool.definition.SkillLoadToolDefinition;
import com.penmate.backend.application.agent.tool.definition.StoryBibleSearchToolDefinition;
import com.penmate.backend.application.agent.tool.definition.StoryBibleUpdateToolDefinition;
import com.penmate.backend.application.agent.tool.definition.TodoPlannerToolDefinition;
import com.penmate.backend.application.agent.tool.definition.ToolExposure;
import com.penmate.backend.application.agent.tool.definition.ToolGovernancePolicy;
import com.penmate.backend.application.agent.tool.definition.ToolLifecycleStatus;
import com.penmate.backend.application.agent.tool.definition.ToolPresentation;
import com.penmate.backend.application.agent.tool.handler.AgentToolHandler;
import com.penmate.backend.application.agent.tool.validation.AgentToolSchemaValidator;
import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import com.penmate.backend.infrastructure.agent.tool.NetworkntAgentToolSchemaValidator;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolRegistryTest {

    @Test
    void all_current_tool_definitions_have_handlers_and_valid_json_schemas() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JacksonJsonCodec jsonCodec = new JacksonJsonCodec(objectMapper);
        SkillPromptRegistry skills = mock(SkillPromptRegistry.class);
        when(skills.listAvailableSkills()).thenReturn(List.of(new SkillCatalogItem("writer", "Write prose")));
        AgentToolDefinitionSource definitions = new InMemoryAgentToolDefinitionSource(List.of(
                new BookCrudToolDefinition(),
                new ChapterEditToolDefinition(),
                new QualityReviewToolDefinition(),
                new RagQueryToolDefinition(),
                new SkillLoadToolDefinition(skills, jsonCodec),
                new StoryBibleSearchToolDefinition(),
                new StoryBibleUpdateToolDefinition(),
                new TodoPlannerToolDefinition()
        ));
        List<AgentToolHandler> handlers = definitions.listAll().stream()
                .map(descriptor -> handler(descriptor.toolCode()))
                .toList();

        AgentToolRegistry registry = new AgentToolRegistry(
                definitions, handlers, new NetworkntAgentToolSchemaValidator(objectMapper));

        assertThat(definitions.listAll()).hasSize(8);
        assertThat(definitions.listLlmSchemas())
                .extracting(schema -> schema.toolCode())
                .doesNotContain("book_crud");
        assertThat(registry.getRequiredDescriptor("book_crud").exposure().lifecycleStatus())
                .isEqualTo(ToolLifecycleStatus.DRAINING);
        assertThat(registry.getRequiredHandler("book_crud")).isNotNull();
    }

    @Test
    void registers_a_one_to_one_definition_handler_pair_and_its_schema() {
        AgentToolDefinitionSource definitions = definitions(descriptor("search"));
        AgentToolHandler handler = handler("search");
        AgentToolSchemaValidator schemas = mock(AgentToolSchemaValidator.class);

        AgentToolRegistry registry = new AgentToolRegistry(definitions, List.of(handler), schemas);

        assertThat(registry.getRequiredHandler("search")).isSameAs(handler);
        verify(schemas).register("search", "{\"type\":\"object\"}");
    }

    @Test
    void rejects_definition_handler_drift_at_startup() {
        assertThatThrownBy(() -> new AgentToolRegistry(
                definitions(descriptor("search")), List.of(handler("other")),
                mock(AgentToolSchemaValidator.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tool definition has no handler: search");
    }

    @Test
    void rejects_duplicate_handlers_at_startup() {
        assertThatThrownBy(() -> new AgentToolRegistry(
                definitions(descriptor("search")), List.of(handler("search"), handler("search")),
                mock(AgentToolSchemaValidator.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicated tool handler: search");
    }

    private AgentToolDefinitionSource definitions(AgentToolDescriptor descriptor) {
        AgentToolDefinitionSource definitions = mock(AgentToolDefinitionSource.class);
        when(definitions.listAll()).thenReturn(List.of(descriptor));
        when(definitions.getRequired(descriptor.toolCode())).thenReturn(descriptor);
        return definitions;
    }

    private AgentToolDescriptor descriptor(String toolCode) {
        return new AgentToolDescriptor(
                toolCode,
                new ToolPresentation("Search"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE, "Search", "{\"type\":\"object\"}"),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 0, Map.of()));
    }

    private AgentToolHandler handler(String toolCode) {
        AgentToolHandler handler = mock(AgentToolHandler.class);
        when(handler.toolCode()).thenReturn(toolCode);
        return handler;
    }
}
