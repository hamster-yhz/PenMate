package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

@Component
public class ToolApprovalViewFactory {

    public ToolApprovalView create(AgentToolDescriptor descriptor, ApprovalPolicyDecision decision) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        if (descriptor.governancePolicy() == null) {
            throw new IllegalArgumentException("descriptor.governancePolicy must not be null");
        }
        String toolDisplayName = descriptor.presentation() == null
                ? null
                : descriptor.presentation().displayName();
        Integer riskLevel = decision.riskLevel() != null
                ? decision.riskLevel()
                : descriptor.governancePolicy().riskLevel();
        return new ToolApprovalView(
                descriptor.toolCode(),
                toolDisplayName,
                riskLevel,
                decision.approvalType(),
                decision.operationCode()
        );
    }
}
