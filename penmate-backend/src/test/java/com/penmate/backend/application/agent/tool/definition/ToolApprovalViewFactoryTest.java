package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolApprovalViewFactoryTest {

    private final ToolApprovalViewFactory factory = new ToolApprovalViewFactory();

    @Test
    void UT_APP_AGENT_TOOL_APPROVAL_VIEW_FACTORY_SHOULD_BUILD_VIEW_FROM_DESCRIPTOR_AND_DECISION() {
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "book_crud",
                new ToolPresentation("书籍 CRUD"),
                new ToolExposure(true, "desc", "{}"),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 3, Map.of())
        );

        ToolApprovalView view = factory.create(
                descriptor,
                new ApprovalPolicyDecision(true, "BOOK_DELETE", 5, "delete", "书籍 CRUD")
        );

        assertThat(view.toolCode()).isEqualTo("book_crud");
        assertThat(view.toolDisplayName()).isEqualTo("书籍 CRUD");
        assertThat(view.riskLevel()).isEqualTo(5);
        assertThat(view.approvalType()).isEqualTo("BOOK_DELETE");
        assertThat(view.operationCode()).isEqualTo("delete");
    }

}
