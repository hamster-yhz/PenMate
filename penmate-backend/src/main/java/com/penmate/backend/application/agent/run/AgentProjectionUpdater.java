package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.repository.AgentRunProjectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentProjectionUpdater {

    private final AgentRunProjectionRepository runProjectionRepository;
    private final ObjectMapper objectMapper;

    public AgentProjectionUpdater(AgentRunProjectionRepository runProjectionRepository, ObjectMapper objectMapper) {
        this.runProjectionRepository = runProjectionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void apply(AgentEvent event) {
        Long latestSequence = runProjectionRepository.findLatestSequence(event.runId());
        if (latestSequence != null && event.sequence() <= latestSequence) {
            return;
        }
        JsonNode payload = readPayload(event.payloadJson());
        switch (event.eventType()) {
            case "run.started" -> runProjectionRepository.updateRunState(
                    event.runId(), "RUNNING", text(payload, "phase", "preflight"), null, event.sequence(), null, null);
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
                    text(payload, "errorCode", null), text(payload, "errorMessage", null), event.sequence());
            case "tool.call.waiting_approval" -> runProjectionRepository.updateRunState(
                    event.runId(), "WAITING_APPROVAL", null, longValue(payload, "approvalId"), event.sequence(), null, null);
            case "approval.approved" -> runProjectionRepository.updateRunState(
                    event.runId(), "RUNNING", null, null, event.sequence(), null, null);
            case "approval.rejected" -> runProjectionRepository.updateRunState(
                    event.runId(), "FAILED", null, null, event.sequence(), text(payload, "errorCode", "approval_rejected"), text(payload, "errorMessage", null));
            case "message.completed" -> runProjectionRepository.updateRunState(
                    event.runId(), null, null, null, event.sequence(),
                    text(payload, "text", null), null);
            case "run.completed" -> runProjectionRepository.updateRunState(event.runId(), "DONE", "completed", null, event.sequence(), null, null);
            case "run.failed" -> runProjectionRepository.updateRunState(
                    event.runId(), "FAILED", "failed", null, event.sequence(), text(payload, "errorCode", null), text(payload, "errorMessage", null));
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