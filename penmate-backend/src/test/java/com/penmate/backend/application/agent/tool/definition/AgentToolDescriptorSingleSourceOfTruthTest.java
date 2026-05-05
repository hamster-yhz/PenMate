package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolDescriptorSingleSourceOfTruthTest {

    @Test
    void UT_APP_AGENT_TOOL_DEFINITION_SOURCE_GET_REQUIRED_SHOULD_EXPOSE_FULL_DESCRIPTOR_WITHOUT_LEGACY_CATALOG_PROJECTION() {
        InMemoryAgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                () -> new AgentToolDescriptor(
                        "book_crud",
                        new ToolPresentation("书籍 CRUD"),
                        new ToolExposure(true, "书籍 CRUD；必须提供 operation", "{\"type\":\"object\"}"),
                        new ToolGovernancePolicy(
                                new ApprovalPolicyDecision(false, ""),
                                2,
                                Map.of("delete", new ToolOperationPolicy("delete", new ApprovalPolicyDecision(true, "BOOK_DELETE")))
                        )
                )
        ));

        AgentToolDescriptor descriptor = source.getRequired("book_crud");

        assertThat(descriptor.toolCode()).isEqualTo("book_crud");
        assertThat(descriptor.presentation().displayName()).isEqualTo("书籍 CRUD");
        assertThat(descriptor.exposure().exposedToLlm()).isTrue();
        assertThat(descriptor.exposure().llmDescription()).isEqualTo("书籍 CRUD；必须提供 operation");
        assertThat(descriptor.exposure().parametersJsonSchema()).isEqualTo("{\"type\":\"object\"}");
        assertThat(descriptor.governancePolicy().defaultDecision().approvalRequired()).isFalse();
        assertThat(descriptor.governancePolicy().defaultDecision().approvalType()).isEqualTo("");
        assertThat(descriptor.governancePolicy().riskLevel()).isEqualTo(2);
        assertThat(descriptor.governancePolicy().operationPolicies()).containsKey("delete");
        assertThat(descriptor.governancePolicy().operationPolicies().get("delete").operationCode()).isEqualTo("delete");
        assertThat(descriptor.governancePolicy().operationPolicies().get("delete").decision().approvalRequired()).isTrue();
        assertThat(descriptor.governancePolicy().operationPolicies().get("delete").decision().approvalType())
                .isEqualTo("BOOK_DELETE");
    }
}
