package com.penmate.backend.application.agent.tool.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.penmate.backend.application.agent.tool.handler.AgentToolHandler;
import com.penmate.backend.domain.agent.run.model.AgentToolCallExecution;
import com.penmate.backend.domain.agent.run.model.AgentToolCallExecutionStatus;
import com.penmate.backend.domain.agent.run.repository.AgentToolCallExecutionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class ToolCallExecutionService {

    private final List<AgentToolHandler> handlers;
    private final AgentToolCallExecutionRepository executions;
    private final BusinessIdGenerator ids;
    private final AgentToolMutationGuard mutationGuard;
    private final ObjectMapper objectMapper;

    public ToolCallExecutionService(List<AgentToolHandler> handlers,
                                    AgentToolCallExecutionRepository executions,
                                    BusinessIdGenerator ids,
                                    AgentToolMutationGuard mutationGuard,
                                    ObjectMapper objectMapper) {
        this.handlers = List.copyOf(handlers);
        this.executions = executions;
        this.ids = ids;
        this.mutationGuard = mutationGuard;
        this.objectMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public ToolCallResult validate(ToolCallRequest request) {
        Optional<AgentToolHandler> handler = findHandler(request == null ? null : request.toolCode());
        if (handler.isEmpty()) {
            String toolCode = request == null ? null : request.toolCode();
            return ToolCallResult.failed("TOOL_HANDLER_NOT_FOUND", "Tool handler not found: " + toolCode);
        }
        try {
            validateIdentity(request);
            handler.get().validate(request);
            mutationGuard.assertExecutable(request, false);
            return null;
        } catch (AgentToolMutationGuard.Rejection rejection) {
            return ToolCallResult.failed(rejection.errorCode(), rejection.getMessage());
        } catch (IllegalArgumentException ex) {
            return ToolCallResult.failed("TOOL_VALIDATION_FAILED", rootMessage(ex));
        }
    }

    public ToolCallResult execute(ToolCallRequest request) {
        ToolCallResult validationFailure = validate(request);
        if (validationFailure != null) {
            log.warn("tool call validation failed: toolCode={}, traceId={}, errorCode={}",
                    request == null ? null : request.toolCode(), request == null ? null : request.traceId(),
                    validationFailure.errorCode());
            return validationFailure;
        }

        AgentToolHandler handler = findHandler(request.toolCode()).orElseThrow();
        String requestSha256 = requestSha256(request);
        AgentToolCallExecution candidate = AgentToolCallExecution.started(
                ids.nextId(), request.runId(), request.toolCallId(), request.toolCode(), requestSha256,
                request.executionToken(), LocalDateTime.now());

        if (!executions.tryInsertStarted(candidate)) {
            return resolveExisting(request, requestSha256);
        }

        try {
            mutationGuard.assertExecutable(request, handler.mutatesState(request));
        } catch (AgentToolMutationGuard.Rejection rejection) {
            ToolCallResult failed = ToolCallResult.failed(rejection.errorCode(), rejection.getMessage());
            return finish(candidate, AgentToolCallExecutionStatus.FAILED, failed)
                    ? failed
                    : resolveAfterLostFinish(request, requestSha256);
        }

        ToolCallResult result;
        try {
            result = handler.execute(request);
            if (result == null) {
                result = ToolCallResult.failed("TOOL_CALL_FAILED", "Tool call returned no result");
            }
        } catch (Exception ex) {
            markAmbiguous(candidate, ex);
            return ToolCallResult.failed("TOOL_CALL_AMBIGUOUS",
                    "Tool execution outcome is unknown and will not be retried automatically");
        }

        if (!"SUCCESS".equals(result.status()) && !"FAILED".equals(result.status())) {
            result = ToolCallResult.failed("TOOL_HANDLER_INVALID_RESULT",
                    "Tool handler returned unsupported execution status: " + result.status());
        }

        AgentToolCallExecutionStatus status = "SUCCESS".equals(result.status())
                ? AgentToolCallExecutionStatus.SUCCEEDED
                : AgentToolCallExecutionStatus.FAILED;
        if (!finish(candidate, status, result)) {
            return resolveAfterLostFinish(request, requestSha256);
        }
        return result;
    }

    private ToolCallResult resolveExisting(ToolCallRequest request, String requestSha256) {
        AgentToolCallExecution existing = executions.find(request.runId(), request.toolCallId());
        if (existing == null) {
            return ToolCallResult.failed("TOOL_CALL_CLAIM_CONFLICT",
                    "Tool call execution could not be claimed");
        }
        if (!existing.matches(request.toolCode(), requestSha256)) {
            return ToolCallResult.failed("TOOL_CALL_REQUEST_MISMATCH",
                    "Tool call id was already used with a different request");
        }
        if (existing.status() == AgentToolCallExecutionStatus.SUCCEEDED
                || existing.status() == AgentToolCallExecutionStatus.FAILED) {
            return replay(existing);
        }
        if (existing.status() == AgentToolCallExecutionStatus.AMBIGUOUS) {
            return ambiguousResult(existing);
        }
        if (existing.executionToken().equals(request.executionToken())) {
            return ToolCallResult.failed("TOOL_CALL_IN_PROGRESS",
                    "Tool call is already executing under the current execution token");
        }

        executions.markFinished(existing.executionId(), existing.executionToken(),
                AgentToolCallExecutionStatus.AMBIGUOUS, null, "TOOL_CALL_AMBIGUOUS",
                "Previous execution lost ownership before recording an outcome", LocalDateTime.now());
        AgentToolCallExecution resolved = executions.find(request.runId(), request.toolCallId());
        if (resolved != null && (resolved.status() == AgentToolCallExecutionStatus.SUCCEEDED
                || resolved.status() == AgentToolCallExecutionStatus.FAILED)) {
            return replay(resolved);
        }
        return ambiguousResult(resolved == null ? existing : resolved);
    }

    private ToolCallResult resolveAfterLostFinish(ToolCallRequest request, String requestSha256) {
        AgentToolCallExecution existing = executions.find(request.runId(), request.toolCallId());
        if (existing != null && existing.matches(request.toolCode(), requestSha256)
                && (existing.status() == AgentToolCallExecutionStatus.SUCCEEDED
                || existing.status() == AgentToolCallExecutionStatus.FAILED)) {
            return replay(existing);
        }
        return ToolCallResult.failed("TOOL_CALL_AMBIGUOUS",
                "Tool execution completed but its durable outcome could not be confirmed");
    }

    private boolean finish(AgentToolCallExecution execution, AgentToolCallExecutionStatus status,
                           ToolCallResult result) {
        return executions.markFinished(execution.executionId(), execution.executionToken(), status,
                json(result), truncate(result.errorCode(), 96), truncate(result.errorMessage(), 500),
                LocalDateTime.now()) == 1;
    }

    private void markAmbiguous(AgentToolCallExecution execution, Exception failure) {
        String message = rootMessage(failure);
        executions.markFinished(execution.executionId(), execution.executionToken(),
                AgentToolCallExecutionStatus.AMBIGUOUS, null, "TOOL_CALL_AMBIGUOUS",
                truncate(message, 500), LocalDateTime.now());
        log.error("tool call outcome is ambiguous: runId={}, toolCallId={}, toolCode={}",
                execution.runId(), execution.toolCallId(), execution.toolCode(), failure);
    }

    private ToolCallResult replay(AgentToolCallExecution execution) {
        if (execution.resultJson() != null && !execution.resultJson().isBlank()) {
            try {
                return objectMapper.readValue(execution.resultJson(), ToolCallResult.class);
            } catch (JsonProcessingException ex) {
                return ToolCallResult.failed("TOOL_CALL_RESULT_CORRUPT",
                        "Stored tool call result is invalid");
            }
        }
        return ToolCallResult.failed(execution.errorCode(), execution.errorMessage());
    }

    private ToolCallResult ambiguousResult(AgentToolCallExecution execution) {
        String message = execution.errorMessage() == null || execution.errorMessage().isBlank()
                ? "Tool execution outcome is unknown and will not be retried automatically"
                : execution.errorMessage();
        return ToolCallResult.failed("TOOL_CALL_AMBIGUOUS", message);
    }

    private String requestSha256(ToolCallRequest request) {
        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("projectId", request.projectId());
        intent.put("runId", request.runId());
        intent.put("sessionId", request.sessionId());
        intent.put("turnId", request.turnId());
        intent.put("operatorId", request.operatorId());
        intent.put("toolCode", request.toolCode());
        try {
            intent.put("arguments", objectMapper.readValue(request.toolArgsJson(), Object.class));
            byte[] canonical = objectMapper.writeValueAsBytes(intent);
            return sha256(canonical);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Tool arguments must be valid JSON", ex);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize tool call result", ex);
        }
    }

    private void validateIdentity(ToolCallRequest request) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        if (request.runId() == null) throw new IllegalArgumentException("runId must not be null");
        if (request.executionToken() == null) throw new IllegalArgumentException("executionToken must not be null");
        if (request.toolCallId() == null || request.toolCallId().isBlank()) {
            throw new IllegalArgumentException("toolCallId must not be blank");
        }
        if (request.toolCallId().length() > 128) {
            throw new IllegalArgumentException("toolCallId must not exceed 128 characters");
        }
        if (request.toolCode() == null || request.toolCode().isBlank()) {
            throw new IllegalArgumentException("toolCode must not be blank");
        }
        if (request.toolCode().length() > 100) {
            throw new IllegalArgumentException("toolCode must not exceed 100 characters");
        }
    }

    private Optional<AgentToolHandler> findHandler(String toolCode) {
        return handlers.stream().filter(handler -> java.util.Objects.equals(handler.toolCode(), toolCode)).findFirst();
    }

    private String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
