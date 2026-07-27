package com.penmate.backend.application.agent.tool.gateway;

import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalView;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalViewFactory;
import com.penmate.backend.application.agent.tool.runtime.AgentRunExecutionContextResolver;
import com.penmate.backend.application.agent.tool.runtime.AgentRunExecutionRejectedException;
import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallExecutionService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.agent.tool.runtime.ToolApprovalPreview;
import com.penmate.backend.application.approval.ApprovalApplicationService;
import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import com.penmate.backend.application.approval.DefaultApprovalPolicyEngine;
import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.application.approval.command.CreateToolApprovalCommand;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.common.serialization.JsonValues;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class ToolCallApplicationService {

    private final AgentToolDefinitionSource toolDefinitionSource;
    private final DefaultApprovalPolicyEngine approvalPolicyEngine;
    private final ToolApprovalViewFactory toolApprovalViewFactory;
    private final ApprovalApplicationService approvalApplicationService;
    private final AgentRunPendingApprovalRepository pendingApprovalRepository;
    private final ToolCallExecutionService toolCallExecutionService;
    private final ToolApprovalPreview toolApprovalPreview;
    private final JsonCodec jsonCodec;
    private final AgentRunExecutionContextResolver executionContexts;

    public ToolCallApplicationService(AgentToolDefinitionSource toolDefinitionSource,
                                      DefaultApprovalPolicyEngine approvalPolicyEngine,
                                      ToolApprovalViewFactory toolApprovalViewFactory,
                                      ApprovalApplicationService approvalApplicationService,
                                      AgentRunPendingApprovalRepository pendingApprovalRepository,
                                      ToolCallExecutionService toolCallExecutionService,
                                      ToolApprovalPreview toolApprovalPreview,
                                      JsonCodec jsonCodec,
                                      AgentRunExecutionContextResolver executionContexts) {
        this.toolDefinitionSource = toolDefinitionSource;
        this.approvalPolicyEngine = approvalPolicyEngine;
        this.toolApprovalViewFactory = toolApprovalViewFactory;
        this.approvalApplicationService = approvalApplicationService;
        this.pendingApprovalRepository = pendingApprovalRepository;
        this.toolCallExecutionService = toolCallExecutionService;
        this.toolApprovalPreview = toolApprovalPreview;
        this.jsonCodec = jsonCodec;
        this.executionContexts = executionContexts;
    }

    public ToolCallResult executeToolCall(ToolCallRequest request) {
        AuthorizedAgentRunContext context;
        try {
            context = executionContexts.resolve(request);
        } catch (AgentRunExecutionRejectedException rejection) {
            return ToolCallResult.failed(rejection.errorCode(), rejection.getMessage());
        }
        log.info("agent.tool.call.start: toolCode={}, projectId={}, runId={}, sessionId={}, traceId={}",
                request.toolCode(), context.projectId(), context.runId(), context.sessionId(), context.traceId());
        ToolCallResult approvalBindingFailure = validateApprovalBinding(context, request);
        if (approvalBindingFailure != null) return approvalBindingFailure;
        AgentToolDescriptor descriptor = toolDefinitionSource.getRequired(request.toolCode());
        ApprovalPolicyDecision decision = approvalPolicyEngine.evaluate(
                descriptor, request, context.input().safetyMode());
        String operationCode = extractOperationCode(request);
        if (decision.approvalRequired()) {
            var approvalPreview = toolApprovalPreview.from(request.toolCode(), request.toolArgsJson());
            ToolCallResult validationFailure = toolCallExecutionService.validate(context, request);
            if (validationFailure != null) return validationFailure;
            AgentRunPendingApproval existing = pendingApprovalRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing != null) {
                if (!matches(existing, context, request)) {
                    return ToolCallResult.failed("TOOL_CALL_REQUEST_MISMATCH",
                            "Tool approval idempotency key was already used by another request");
                }
                if ("PENDING".equals(existing.pendingStatus())
                        || "APPROVED".equals(existing.pendingStatus())
                        || "RESUMING".equals(existing.pendingStatus())) {
                    return ToolCallResult.waitingApproval(existing.approvalId(), approvalPreview);
                }
                return ToolCallResult.failed("TOOL_APPROVAL_NOT_EXECUTABLE",
                        "Existing tool approval is already terminal: " + existing.pendingStatus());
            }
            ToolApprovalView approvalView = toolApprovalViewFactory.create(descriptor, decision);
            String approvalBinding = approvalBinding(context, request);
            ApprovalRequest approvalRequest = approvalApplicationService.createForTool(new CreateApprovalCommand(
                    context.projectId(),
                    context.runId(),
                    decision.approvalType(),
                    request.toolArgsJson(),
                    approvalView.riskLevel() == null ? descriptor.governancePolicy().riskLevel() : approvalView.riskLevel(),
                    context.ownerUserId()
            ), new CreateToolApprovalCommand(
                    context.runId(), context.projectId(), context.sessionId(), context.turnId(),
                    request.toolCallId(), request.toolCode(), request.toolArgsJson(), request.continuationJson(),
                    request.conversationMessagesJson(), request.idempotencyKey(), context.ownerUserId(),
                    context.traceId(), approvalBinding
            ), context.traceId());
            log.info("agent.tool.call.waiting_approval: toolCode={}, operationCode={}, approvalId={}, runId={}, traceId={}",
                    request.toolCode(), operationCode, approvalRequest.getApprovalRequestId(), context.runId(), context.traceId());
            return ToolCallResult.waitingApproval(approvalRequest.getApprovalRequestId(), approvalPreview);
        }
        ToolCallResult result = toolCallExecutionService.execute(context, request);
        if (result != null && "SUCCESS".equals(result.status())) {
            log.info("agent.tool.call.success: toolCode={}, operationCode={}, runId={}, traceId={}",
                    request.toolCode(), operationCode, context.runId(), context.traceId());
        } else {
            log.warn("agent.tool.call.failed: toolCode={}, operationCode={}, status={}, errorCode={}, errorMessage={}, runId={}, traceId={}",
                    request.toolCode(), operationCode,
                    result == null ? null : result.status(),
                    result == null ? null : result.errorCode(),
                    result == null ? "Tool call returned no result" : result.errorMessage(),
                    context.runId(), context.traceId());
        }
        return result;
    }

    private String extractOperationCode(ToolCallRequest request) {
        try {
            String operation = JsonValues.string(jsonCodec.readObject(request.toolArgsJson()), "operation");
            return operation == null || operation.isBlank() ? null : operation.trim();
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean matches(AgentRunPendingApproval pending, AuthorizedAgentRunContext context,
                            ToolCallRequest request) {
        return java.util.Objects.equals(pending.runId(), context.runId())
                && java.util.Objects.equals(pending.projectId(), context.projectId())
                && java.util.Objects.equals(pending.sessionId(), context.sessionId())
                && java.util.Objects.equals(pending.turnId(), context.turnId())
                && java.util.Objects.equals(pending.toolCallId(), request.toolCallId())
                && java.util.Objects.equals(pending.toolCode(), request.toolCode())
                && java.util.Objects.equals(pending.toolArgsJson(), request.toolArgsJson());
    }

    private ToolCallResult validateApprovalBinding(AuthorizedAgentRunContext context, ToolCallRequest request) {
        if (!"APPROVED".equals(request.resumeMode())) return null;
        try {
            Map<String, Object> binding = jsonCodec.readObject(request.approvalSummaryJson());
            boolean matches = java.util.Objects.equals(binding.get("toolCode"), request.toolCode())
                    && java.util.Objects.equals(binding.get("toolArgsHash"), argsHash(request.toolArgsJson()))
                    && java.util.Objects.equals(longValue(binding.get("contextEpochId")), context.contextEpochId())
                    && java.util.Objects.equals(binding.get("safetyMode"), context.input().safetyMode())
                    && java.util.Objects.equals(binding.get("expectedState"), expectedState(jsonCodec.read(request.toolArgsJson())));
            return matches ? null : ToolCallResult.failed("TOOL_APPROVAL_STALE",
                    "Approved tool request no longer matches its immutable approval binding");
        } catch (RuntimeException exception) {
            return ToolCallResult.failed("TOOL_APPROVAL_STALE", "Approved tool request has an invalid approval binding");
        }
    }

    private String approvalBinding(AuthorizedAgentRunContext context, ToolCallRequest request) {
        Map<String, Object> binding = new java.util.LinkedHashMap<>();
        binding.put("toolCode", request.toolCode());
        binding.put("toolArgsHash", argsHash(request.toolArgsJson()));
        binding.put("contextEpochId", context.contextEpochId());
        binding.put("safetyMode", context.input().safetyMode());
        binding.put("expectedState", expectedState(jsonCodec.read(request.toolArgsJson())));
        return jsonCodec.writeCanonical(binding);
    }

    private Object expectedState(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new java.util.TreeMap<>();
            for (var entry : source.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object nested = expectedState(entry.getValue());
                if (key.startsWith("expected") || nested instanceof Map<?, ?> map && !map.isEmpty()
                        || nested instanceof java.util.List<?> list && !list.isEmpty()) {
                    result.put(key, key.startsWith("expected") ? entry.getValue() : nested);
                }
            }
            return result;
        }
        if (value instanceof java.util.List<?> source) {
            java.util.List<Object> result = new java.util.ArrayList<>();
            for (int index = 0; index < source.size(); index++) {
                Object nested = expectedState(source.get(index));
                if (nested instanceof Map<?, ?> map && !map.isEmpty()) {
                    result.add(Map.of("index", index, "value", nested));
                }
            }
            return result;
        }
        return null;
    }

    private String argsHash(String argsJson) {
        try {
            String canonical = jsonCodec.writeCanonical(jsonCodec.read(argsJson));
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private Long longValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.valueOf(String.valueOf(value));
    }
}
