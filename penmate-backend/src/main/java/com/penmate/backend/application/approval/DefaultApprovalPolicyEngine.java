package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.definition.ToolGovernancePolicy;
import com.penmate.backend.application.agent.tool.definition.ToolOperationPolicy;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.common.serialization.JsonValues;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class DefaultApprovalPolicyEngine {

    private final JsonCodec jsonCodec;

    public DefaultApprovalPolicyEngine(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public ApprovalPolicyDecision evaluate(AgentToolDescriptor descriptor, ToolCallRequest request) {
        if (descriptor == null || request == null) {
            throw new IllegalArgumentException("descriptor and request must not be null");
        }
        if ("APPROVED".equals(request.resumeMode())
                && request.approvalSummaryJson() != null && !request.approvalSummaryJson().isBlank()) {
            return new ApprovalPolicyDecision(false, "");
        }
        ToolGovernancePolicy governance = descriptor.governancePolicy();
        String operation = extractOperationCode(request.toolArgsJson());
        ToolOperationPolicy operationPolicy = findOperationPolicy(governance, operation);
        ApprovalPolicyDecision resolved = enrichDecision(
                operationPolicy == null ? governance.defaultDecision() : operationPolicy.decision(),
                governance.riskLevel(), operation, descriptor.presentation().displayName());
        log.debug("approval policy evaluated: toolCode={}, operation={}, required={}, runId={}",
                descriptor.toolCode(), operation, resolved.approvalRequired(), request.runId());
        return resolved;
    }

    private ToolOperationPolicy findOperationPolicy(ToolGovernancePolicy governance, String operation) {
        if (governance == null || operation == null) return null;
        Map<String, ToolOperationPolicy> policies = governance.operationPolicies();
        return policies == null ? null : policies.get(operation);
    }

    private ApprovalPolicyDecision enrichDecision(ApprovalPolicyDecision decision,
                                                   Integer riskLevel,
                                                   String operation,
                                                   String displayName) {
        ApprovalPolicyDecision safe = decision == null ? new ApprovalPolicyDecision(false, "") : decision;
        return new ApprovalPolicyDecision(
                safe.approvalRequired(),
                safe.approvalType(),
                safe.riskLevel() == null ? riskLevel : safe.riskLevel(),
                safe.operationCode() == null ? operation : safe.operationCode(),
                safe.displayName() == null ? displayName : safe.displayName());
    }

    private String extractOperationCode(String argumentsJson) {
        try {
            String operation = JsonValues.string(jsonCodec.readObject(argumentsJson), "operation");
            return operation == null || operation.isBlank() ? null : operation.trim();
        } catch (RuntimeException exception) {
            log.warn("tool operation could not be parsed; using default approval policy", exception);
            return null;
        }
    }
}
