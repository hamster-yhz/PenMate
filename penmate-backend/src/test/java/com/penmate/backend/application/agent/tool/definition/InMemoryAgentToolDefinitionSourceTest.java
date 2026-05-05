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
    void UT_APP_AGENT_TOOL_DEFINITION_SOURCE_GET_REQUIRED_SHOULD_RETURN_DESCRIPTOR_DECLARED_BY_TOOL_DEFINITION() {
        InMemoryAgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                new TestToolDefinition(
                        "context_enhancer",
                        "上下文增强",
                        true,
                        "补充上下文",
                        "{\"type\":\"object\"}",
                        new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
                ),
                new TestToolDefinition(
                        "book_crud",
                        "书籍 CRUD",
                        true,
                        "书籍 CRUD；必须提供 operation",
                        "{\"type\":\"object\",\"properties\":{\"operation\":{\"type\":\"string\"}}}",
                        new ToolGovernancePolicy(
                                new ApprovalPolicyDecision(false, ""),
                                2,
                                Map.of("delete", new ToolOperationPolicy("delete", new ApprovalPolicyDecision(true, "BOOK_DELETE")))
                        )
                )
        ));

        AgentToolDescriptor contextEnhancer = source.getRequired("context_enhancer");
        AgentToolDescriptor bookCrud = source.getRequired("book_crud");

        assertThat(contextEnhancer.toolCode()).isEqualTo("context_enhancer");
        assertThat(contextEnhancer.presentation().displayName()).isEqualTo("上下文增强");
        assertThat(contextEnhancer.exposure().exposedToLlm()).isTrue();
        assertThat(contextEnhancer.governancePolicy().defaultDecision().approvalRequired()).isFalse();

        assertThat(bookCrud.toolCode()).isEqualTo("book_crud");
        assertThat(bookCrud.presentation().displayName()).isEqualTo("书籍 CRUD");
        assertThat(bookCrud.exposure().exposedToLlm()).isTrue();
        assertThat(bookCrud.governancePolicy().defaultDecision().approvalType()).isEqualTo("");
    }

    @Test
    void UT_APP_AGENT_TOOL_DEFINITION_SOURCE_LIST_ALL_SHOULD_RETURN_ALL_REGISTERED_DESCRIPTORS_IN_DECLARATION_ORDER() {
        AgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                new TestToolDefinition(
                        "tool_a",
                        "工具 A",
                        true,
                        "desc a",
                        "{}",
                        new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
                ),
                new TestToolDefinition(
                        "tool_b",
                        "工具 B",
                        false,
                        "desc b",
                        "{}",
                        new ToolGovernancePolicy(new ApprovalPolicyDecision(true, "REVIEW"), 3, Map.of())
                ),
                new TestToolDefinition(
                        "tool_c",
                        "工具 C",
                        true,
                        "desc c",
                        "{}",
                        new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 2, Map.of())
                )
        ));

        assertThat(source.listAll())
                .extracting(AgentToolDescriptor::toolCode)
                .containsExactly("tool_a", "tool_b", "tool_c");
    }

    @Test
    void UT_APP_AGENT_TOOL_DEFINITION_SOURCE_LIST_ALL_SHOULD_RETURN_IMMUTABLE_SNAPSHOT() {
        AgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                new TestToolDefinition(
                        "tool_a",
                        "工具 A",
                        true,
                        "desc a",
                        "{}",
                        new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
                )
        ));

        assertThatThrownBy(() -> source.listAll().add(new AgentToolDescriptor(
                "tool_b",
                new ToolPresentation("工具 B"),
                new ToolExposure(true, "desc b", "{}"),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
        )))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void UT_APP_AGENT_TOOL_DEFINITION_SOURCE_GET_REQUIRED_SHOULD_THROW_WHEN_TOOL_CODE_NOT_FOUND() {
        InMemoryAgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                new TestToolDefinition(
                        "context_enhancer",
                        "上下文增强",
                        true,
                        "补充上下文",
                        "{}",
                        new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
                )
        ));

        assertThatThrownBy(() -> source.getRequired("missing_tool"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tool descriptor not found: missing_tool");
    }

    @Test
    void UT_APP_AGENT_TOOL_DEFINITION_SOURCE_LIST_LLM_SCHEMAS_SHOULD_USE_EXPOSURE_DESCRIPTION_AS_SINGLE_SOURCE_OF_TRUTH() {
        InMemoryAgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                new TestToolDefinition(
                        "context_enhancer",
                        "展示名称不会进入 llm schema",
                        true,
                        "补充上下文",
                        "{\"type\":\"object\"}",
                        new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
                ),
                new TestToolDefinition(
                        "book_crud",
                        "另一个展示名称",
                        true,
                        "书籍 CRUD；必须提供 operation",
                        "{\"type\":\"object\",\"properties\":{\"operation\":{\"type\":\"string\"}}}",
                        new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 2, Map.of())
                ),
                new TestToolDefinition(
                        "internal_audit",
                        "内部审计",
                        false,
                        "内部审计工具",
                        "{\"type\":\"object\"}",
                        new ToolGovernancePolicy(new ApprovalPolicyDecision(true, "AUDIT"), 3, Map.of())
                )
        ));

        Map<String, AgentLlmToolSchema> schemasByToolCode = source.listLlmSchemas().stream()
                .collect(Collectors.toMap(AgentLlmToolSchema::toolCode, schema -> schema));

        assertThat(schemasByToolCode.keySet())
                .containsExactlyInAnyOrder("context_enhancer", "book_crud");
        assertThat(schemasByToolCode).doesNotContainKey("internal_audit");
        assertThat(schemasByToolCode.get("context_enhancer").description()).isEqualTo("补充上下文");
        assertThat(schemasByToolCode.get("book_crud").description())
                .contains("书籍 CRUD")
                .contains("operation");
    }

    @Test
    void UT_APP_AGENT_TOOL_DEFINITION_SOURCE_GET_REQUIRED_SHOULD_EXPOSE_OPERATION_POLICY_DECLARED_BY_TOOL_DEFINITION() {
        InMemoryAgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                new TestToolDefinition(
                        "book_crud",
                        "书籍 CRUD",
                        true,
                        "书籍 CRUD；必须提供 operation",
                        "{}",
                        new ToolGovernancePolicy(
                                new ApprovalPolicyDecision(false, ""),
                                2,
                                Map.of("delete", new ToolOperationPolicy("delete", new ApprovalPolicyDecision(true, "BOOK_DELETE")))
                        )
                )
        ));

        AgentToolDescriptor descriptor = source.getRequired("book_crud");
        ToolOperationPolicy deletePolicy = descriptor.governancePolicy().operationPolicies().get("delete");

        assertThat(deletePolicy).isNotNull();
        assertThat(deletePolicy.operationCode()).isEqualTo("delete");
        assertThat(deletePolicy.decision().approvalRequired()).isTrue();
        assertThat(deletePolicy.decision().approvalType()).isEqualTo("BOOK_DELETE");
    }

    @Test
    void UT_APP_AGENT_TOOL_DEFINITION_SOURCE_CONSTRUCTOR_SHOULD_REJECT_DUPLICATED_TOOL_CODES() {
        assertThatThrownBy(() -> new InMemoryAgentToolDefinitionSource(List.of(
                new TestToolDefinition(
                        "book_crud",
                        "书籍 CRUD A",
                        true,
                        "A",
                        "{}",
                        new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
                ),
                new TestToolDefinition(
                        "book_crud",
                        "书籍 CRUD B",
                        true,
                        "B",
                        "{}",
                        new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 2, Map.of())
                )
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicated tool definition: book_crud");
    }

    @Test
    void UT_APP_AGENT_TOOL_DEFINITION_SOURCE_CONSTRUCTOR_SHOULD_REJECT_DESCRIPTOR_WITH_MISSING_EXPOSURE() {
        assertThatThrownBy(() -> new InMemoryAgentToolDefinitionSource(List.of(
                () -> new AgentToolDescriptor(
                        "book_crud",
                        new ToolPresentation("书籍 CRUD"),
                        null,
                        new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 2, Map.of())
                )
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exposure");
    }

    @Test
    void UT_APP_AGENT_TOOL_DEFINITION_SOURCE_CONSTRUCTOR_SHOULD_REJECT_DESCRIPTOR_WITH_MISSING_GOVERNANCE_POLICY() {
        assertThatThrownBy(() -> new InMemoryAgentToolDefinitionSource(List.of(
                () -> new AgentToolDescriptor(
                        "book_crud",
                        new ToolPresentation("书籍 CRUD"),
                        new ToolExposure(true, "书籍 CRUD；必须提供 operation", "{}"),
                        null
                )
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("governancePolicy");
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
