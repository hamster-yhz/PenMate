package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.repository.AgentRunProjectionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentProjectionUpdater {

    private static final int ERROR_CODE_MAX_LENGTH = 96;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 500;

    private final AgentRunProjectionRepository runProjectionRepository;
    private final AgentSessionRepository agentSessionRepository;
    private final BusinessIdGenerator businessIdGenerator;
    private final ObjectMapper objectMapper;
    private final AgentEventPayloadResolver payloadResolver;

    public AgentProjectionUpdater(AgentRunProjectionRepository runProjectionRepository,
                                  AgentSessionRepository agentSessionRepository,
                                  BusinessIdGenerator businessIdGenerator,
                                  ObjectMapper objectMapper,
                                  AgentEventPayloadResolver payloadResolver) {
        this.runProjectionRepository = runProjectionRepository;
        this.agentSessionRepository = agentSessionRepository;
        this.businessIdGenerator = businessIdGenerator;
        this.objectMapper = objectMapper;
        this.payloadResolver = payloadResolver;
    }

    @Transactional
    public void apply(AgentEvent event) {
        Long latestSequence = runProjectionRepository.findLatestSequence(event.runId());
        if (latestSequence != null && event.sequence() <= latestSequence) {
            return;
        }
        JsonNode payload = readPayload(payloadResolver.resolve(event).payloadJson());
        switch (event.eventType()) {
            case "run.started" -> runProjectionRepository.updateRunState(
                    event.runId(), "RUNNING", text(payload, "phase", "routing"), null, event.sequence(), null, null);
            case "run.phase.changed" -> runProjectionRepository.updateRunState(
                    event.runId(), text(payload, "status", null), text(payload, "phase", null), null, event.sequence(), null, null);
            case "tool.call.started" -> runProjectionRepository.upsertToolCall(
                    event.runId(), text(payload, "toolCallId", ""), text(payload, "toolCode", ""), text(payload, "toolName", null),
                    "running", intValue(payload, "iteration"), raw(payload, "argumentsPreview"), null, null, null, null, null, event.sequence());
            case "tool.call.completed" -> runProjectionRepository.upsertToolCall(
                    event.runId(), text(payload, "toolCallId", ""), text(payload, "toolCode", ""), text(payload, "toolName", null),
                    "success", intValue(payload, "iteration"), raw(payload, "argumentsPreview"), text(payload, "outputPreview", null),
                    longValue(payload, "outputArtifactId"), null, null, null, event.sequence());
            case "tool.call.failed" -> runProjectionRepository.upsertToolCall(
                    event.runId(), text(payload, "toolCallId", ""), text(payload, "toolCode", ""), text(payload, "toolName", null),
                    "failed", intValue(payload, "iteration"), raw(payload, "argumentsPreview"), null, null, null,
                    errorCode(payload, "AGENT_TOOL_CALL_FAILED"), errorMessage(payload), event.sequence());
            case "tool.call.waiting_approval" -> runProjectionRepository.updateRunState(
                    event.runId(), "WAITING_APPROVAL", null, longValue(payload, "approvalId"), event.sequence(), null, null);
            case "approval.approved" -> runProjectionRepository.updateRunState(
                    event.runId(), "RUNNING", null, null, event.sequence(), null, null);
            case "approval.rejected" -> runProjectionRepository.updateRunState(
                    event.runId(), "CANCELLED", "cancelled", null, event.sequence(),
                    errorCode(payload, "AGENT_APPROVAL_REJECTED"), errorMessage(payload));
            case "run.suspended" -> runProjectionRepository.updateRunState(
                    event.runId(), "SUSPENDED", "suspended", null, event.sequence(),
                    errorCode(payload, "AGENT_RUN_TRANSIENT_FAILURE"), errorMessage(payload));
            case "run.cancelled" -> runProjectionRepository.updateRunState(
                    event.runId(), "CANCELLED", "cancelled", null, event.sequence(),
                    errorCode(payload, "AGENT_RUN_CANCELLED"), errorMessage(payload));
            case "run.superseded" -> runProjectionRepository.updateRunState(
                    event.runId(), "SUPERSEDED", "superseded", null, event.sequence(),
                    errorCode(payload, "AGENT_RUN_SUPERSEDED"), errorMessage(payload));
            case "message.completed" -> persistAssistantMessage(event, payload);
            case "run.completed" -> runProjectionRepository.updateRunState(event.runId(), "DONE", "completed", null, event.sequence(), null, null);
            case "run.failed" -> runProjectionRepository.updateRunState(
                    event.runId(), "FAILED", "failed", null, event.sequence(), errorCode(payload, "AGENT_RUN_FAILED"), errorMessage(payload));
            case "todo.created" -> runProjectionRepository.upsertTodo(
                    event.runId(),
                    text(payload, "todoId", ""),
                    text(payload, "title", ""),
                    text(payload, "status", "TODO"),
                    intValue(payload, "sortOrder"),
                    text(payload, "blockedReason", null),
                    text(payload, "errorSummary", null),
                    text(payload, "completedSummary", null),
                    event.sequence());
            case "todo.updated" -> runProjectionRepository.upsertTodo(
                    event.runId(),
                    text(payload, "todoId", ""),
                    text(payload, "title", null),
                    text(payload, "status", null),
                    intValue(payload, "sortOrder"),
                    text(payload, "blockedReason", null),
                    text(payload, "errorSummary", null),
                    text(payload, "completedSummary", null),
                    event.sequence());
            case "todo.completed" -> runProjectionRepository.upsertTodo(
                    event.runId(),
                    text(payload, "todoId", ""),
                    text(payload, "title", null),
                    "DONE",
                    null,
                    null,
                    null,
                    text(payload, "completedSummary", null),
                    event.sequence());
            case "todo.deleted" -> runProjectionRepository.deleteTodo(
                    event.runId(),
                    text(payload, "todoId", ""),
                    event.sequence());
            default -> runProjectionRepository.advanceLatestSequence(event.runId(), event.sequence());
        }
    }

    private JsonNode readPayload(String payloadJson) {
        try {
            return objectMapper.readTree(payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid agent event payload JSON", ex);
        }
    }

    private String text(JsonNode payload, String fieldName, String defaultValue) {
        JsonNode node = payload.get(fieldName);
        return node == null || node.isNull() ? defaultValue : node.asText(defaultValue);
    }

    private void persistAssistantMessage(AgentEvent event, JsonNode payload) {
        String role = text(payload, "role", "assistant");
        String content = text(payload, "text", "");
        if (!"assistant".equalsIgnoreCase(role) || content.isBlank()) {
            runProjectionRepository.advanceLatestSequence(event.runId(), event.sequence());
            return;
        }

        Long existingMessageId = agentSessionRepository.findTurnAssistantMessageId(event.sessionId(), event.turnId());
        if (existingMessageId != null && existingMessageId > 0) {
            runProjectionRepository.setCurrentAssistantMessage(event.runId(), existingMessageId, event.sequence());
            return;
        }

        Long messageId = businessIdGenerator.nextId();
        int seqNo = agentSessionRepository.nextMessageSeq(event.sessionId());
        int inserted = agentSessionRepository.insertSessionMessage(
                event.sessionId(),
                event.turnId(),
                messageId,
                "assistant",
                "CHAT",
                content,
                seqNo
        );
        if (inserted != 1) {
            throw new IllegalStateException("failed to insert assistant message");
        }
        agentSessionRepository.updateTurnAssistantMessage(event.sessionId(), event.turnId(), messageId);
        runProjectionRepository.setCurrentAssistantMessage(event.runId(), messageId, event.sequence());
    }

    private String errorCode(JsonNode payload, String defaultValue) {
        return truncate(text(payload, "errorCode", defaultValue), ERROR_CODE_MAX_LENGTH);
    }

    private String errorMessage(JsonNode payload) {
        return truncate(text(payload, "errorMessage", null), ERROR_MESSAGE_MAX_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Long longValue(JsonNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        return node == null || node.isNull() ? null : node.asLong();
    }

    private Integer intValue(JsonNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        return node == null || node.isNull() ? null : node.asInt();
    }

    private String raw(JsonNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        return node == null || node.isNull() ? null : node.toString();
    }
}
