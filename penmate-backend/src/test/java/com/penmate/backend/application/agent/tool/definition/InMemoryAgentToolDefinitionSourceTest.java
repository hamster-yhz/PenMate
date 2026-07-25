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
    void exposes_descriptors_in_declaration_order_as_an_immutable_snapshot() {
        InMemoryAgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                definition("tool_a", ToolLifecycleStatus.ACTIVE, governance(false, "", 1)),
                definition("book_crud", ToolLifecycleStatus.ACTIVE, new ToolGovernancePolicy(
                        new ApprovalPolicyDecision(false, ""), 2,
                        Map.of("delete", new ToolOperationPolicy("delete", new ApprovalPolicyDecision(true, "BOOK_DELETE")))))
        ));

        assertThat(source.listAll()).extracting(AgentToolDescriptor::toolCode)
                .containsExactly("tool_a", "book_crud");
        ApprovalPolicyDecision deleteDecision = source.getRequired("book_crud")
                .governancePolicy().operationPolicies().get("delete").decision();
        assertThat(deleteDecision.approvalRequired()).isTrue();
        assertThat(deleteDecision.approvalType()).isEqualTo("BOOK_DELETE");
        assertThatThrownBy(() -> source.listAll().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejects_unknown_tool_codes() {
        InMemoryAgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                definition("custom_tool", ToolLifecycleStatus.ACTIVE, governance(false, "", 1))));

        assertThatThrownBy(() -> source.getRequired("missing_tool"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tool descriptor not found: missing_tool");
    }

    @Test
    void derives_llm_schemas_only_from_active_descriptors() {
        InMemoryAgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                definition("custom_tool", ToolLifecycleStatus.ACTIVE, governance(false, "", 1)),
                definition("book_crud", ToolLifecycleStatus.ACTIVE, governance(false, "", 2)),
                definition("internal_audit", ToolLifecycleStatus.DRAINING, governance(true, "AUDIT", 3)),
                definition("retired_tool", ToolLifecycleStatus.DISABLED, governance(false, "", 1))
        ));

        Map<String, AgentLlmToolSchema> schemas = source.listLlmSchemas().stream()
                .collect(Collectors.toMap(AgentLlmToolSchema::toolCode, schema -> schema));

        assertThat(schemas.keySet()).containsExactlyInAnyOrder("custom_tool", "book_crud");
        assertThat(schemas).doesNotContainKey("internal_audit");
        assertThat(schemas).doesNotContainKey("retired_tool");
        assertThat(schemas.get("book_crud").description()).isEqualTo("book_crud description");
    }

    @Test
    void exposes_chapter_edit_and_todo_planner_without_the_removed_draft_tool() {
        InMemoryAgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                new ChapterEditToolDefinition(), new TodoPlannerToolDefinition()));
        Map<String, AgentLlmToolSchema> schemas = source.listLlmSchemas().stream()
                .collect(Collectors.toMap(AgentLlmToolSchema::toolCode, schema -> schema));

        assertThatThrownBy(() -> source.getRequired("draft_generation"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(schemas).doesNotContainKey("draft_generation");
        assertThat(schemas.get("chapter_edit").parametersJsonSchema()).contains("\"chapterId\"");
        assertThat(source.getRequired("todo_planner").presentation().displayName()).isNotBlank();
        assertThat(schemas.get("todo_planner").parametersJsonSchema())
                .contains("\"operation\"", "\"create\"", "\"complete\"")
                .doesNotContain("planningMode", "sessionId");
    }

    @Test
    void rejects_duplicate_tool_codes() {
        assertThatThrownBy(() -> new InMemoryAgentToolDefinitionSource(List.of(
                definition("book_crud", ToolLifecycleStatus.ACTIVE, governance(false, "", 1)),
                definition("book_crud", ToolLifecycleStatus.ACTIVE, governance(false, "", 2)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicated tool definition: book_crud");
    }

    @Test
    void rejects_descriptors_missing_required_contract_sections() {
        List<AgentToolDefinition> invalidDefinitions = List.of(
                () -> new AgentToolDescriptor("book_crud", new ToolPresentation("Book CRUD"), null,
                        governance(false, "", 2)),
                () -> new AgentToolDescriptor("book_crud", new ToolPresentation("Book CRUD"),
                        new ToolExposure(ToolLifecycleStatus.ACTIVE, "description", "{}"), null)
        );

        assertThatThrownBy(() -> new InMemoryAgentToolDefinitionSource(List.of(invalidDefinitions.get(0))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exposure");
        assertThatThrownBy(() -> new InMemoryAgentToolDefinitionSource(List.of(invalidDefinitions.get(1))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("governancePolicy");
    }

    private AgentToolDefinition definition(String toolCode, ToolLifecycleStatus status, ToolGovernancePolicy governance) {
        return () -> new AgentToolDescriptor(
                toolCode,
                new ToolPresentation(toolCode + " display"),
                new ToolExposure(status, toolCode + " description", "{\"type\":\"object\"}"),
                governance
        );
    }

    private static ToolGovernancePolicy governance(boolean approvalRequired, String approvalType, int riskLevel) {
        return new ToolGovernancePolicy(
                new ApprovalPolicyDecision(approvalRequired, approvalType), riskLevel, Map.of());
    }
}
