package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.definition.ToolGovernancePolicy;
import com.penmate.backend.application.agent.tool.definition.ToolOperationPolicy;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 默认审批策略引擎。
 */
@Component
@Slf4j
public class DefaultApprovalPolicyEngine {

    /**
     * 评估一次 tool 调用的审批要求。
     *
     * @param descriptor tool descriptor
     * @param request 本次 tool 调用请求
     * @return 审批决策结果
     */
    public ApprovalPolicyDecision evaluate(AgentToolDescriptor descriptor, ToolCallRequest request) {
        if (descriptor == null || request == null) {
            throw new IllegalArgumentException("descriptor and request must not be null");
        }
        log.debug("开始评估审批策略: toolCode={}, traceId={}", descriptor.toolCode(), request.traceId());

        ToolGovernancePolicy governancePolicy = descriptor.governancePolicy();
        String operationCode = extractOperationCode(request.toolArgsJson());
        ToolOperationPolicy operationPolicy = findOperationPolicy(governancePolicy, operationCode);
        if (operationPolicy != null) {
            ApprovalPolicyDecision resolved = enrichDecision(
                    operationPolicy.decision(),
                    governancePolicy.riskLevel(),
                    operationCode,
                    descriptor.presentation().displayName()
            );
            log.info("审批策略命中 operation policy: toolCode={}, operationCode={}, approvalType={}, traceId={}",
                    descriptor.toolCode(), operationCode, resolved.approvalType(), request.traceId());
            return resolved;
        }

        ApprovalPolicyDecision resolved = enrichDecision(
                governancePolicy.defaultDecision(),
                governancePolicy.riskLevel(),
                operationCode,
                descriptor.presentation().displayName()
        );
        log.info("审批策略命中默认治理策略: toolCode={}, operationCode={}, approvalRequired={}, approvalType={}, traceId={}",
                descriptor.toolCode(), operationCode, resolved.approvalRequired(), resolved.approvalType(), request.traceId());
        return resolved;
    }

    private ToolOperationPolicy findOperationPolicy(ToolGovernancePolicy governancePolicy, String operationCode) {
        if (governancePolicy == null || operationCode == null) {
            return null;
        }
        Map<String, ToolOperationPolicy> operationPolicies = governancePolicy.operationPolicies();
        if (operationPolicies == null) {
            return null;
        }
        return operationPolicies.get(operationCode);
    }

    private ApprovalPolicyDecision enrichDecision(ApprovalPolicyDecision baseDecision,
                                                  Integer riskLevel,
                                                  String operationCode,
                                                  String displayName) {
        ApprovalPolicyDecision safeDecision = baseDecision == null
                ? new ApprovalPolicyDecision(false, "")
                : baseDecision;
        return new ApprovalPolicyDecision(
                safeDecision.approvalRequired(),
                safeDecision.approvalType(),
                safeDecision.riskLevel() != null ? safeDecision.riskLevel() : riskLevel,
                safeDecision.operationCode() != null ? safeDecision.operationCode() : operationCode,
                safeDecision.displayName() != null ? safeDecision.displayName() : displayName
        );
    }

    private String extractOperationCode(String toolArgsJson) {
        try {
            String operationCode = AgentJsonCodec.parseObj(toolArgsJson).getStr("operation", null);
            return operationCode == null || operationCode.isBlank() ? null : operationCode;
        } catch (Exception ex) {
            log.warn("解析 tool operation 失败，按默认治理策略处理: rawArgs={}", toolArgsJson, ex);
            return null;
        }
    }
}
