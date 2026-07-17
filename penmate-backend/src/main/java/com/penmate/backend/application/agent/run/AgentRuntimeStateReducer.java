package com.penmate.backend.application.agent.run;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;

import java.util.List;

@Component
public class AgentRuntimeStateReducer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public AgentRuntimeState applyAll(AgentRuntimeState initialState, List<AgentEvent> events) {
        AgentRuntimeState state = initialState;
        for (AgentEvent event : events) {
            state = apply(state, event);
        }
        return state;
    }

    public AgentRuntimeState apply(AgentRuntimeState state, AgentEvent event) {
        if (event.sequence() <= state.lastEventSeq()) {
            return state;
        }
        JsonNode payload = readPayload(event.payloadJson());
        return switch (event.eventType()) {
            case "run.started" -> state.withStatusAndPhase("RUNNING", "routing", event.sequence());
            case "run.phase.changed" -> state.withStatusAndPhase(
                    statusOrCurrent(state, payload),
                    text(payload, "phase", state.phase()),
                    event.sequence());
            case "turn.route.completed", "context.epoch.bound" -> state.withLastEventSeq(event.sequence());
            case "context.resolved", "prompt.composed", "llm.continuation.saved" ->
                    state.withArtifactAdded(longValue(payload, "artifactId"), event.sequence());
            case "llm.turn.started" -> state.withLlmTurn(
                    intValue(payload, "llmTurnIndex"),
                    state.tokenUsage(),
                    event.sequence());
            case "llm.turn.completed" -> state.withLlmTurn(
                    state.llmTurnIndex(),
                    addUsage(state.tokenUsage(), payload),
                    event.sequence());
            case "tool.call.started" -> state.withToolCallWaiting(
                    text(payload, "toolCallId", ""),
                    state.assistantToolCallsJson(),
                    Math.max(0, state.remainingToolCalls() - 1),
                    event.sequence());
            case "tool.call.completed", "tool.call.failed" -> state.withLastEventSeq(event.sequence());
            case "tool.call.waiting_approval" -> applyToolCallWaitingApproval(state, payload, event.sequence());
            case "approval.requested" -> state.withActiveApproval(longValue(payload, "approvalId"), event.sequence());
            case "approval.approved" -> state.withToolCallApproved(
                    text(payload, "approvedPayload", ""),
                    event.sequence());
            case "approval.rejected", "run.cancelled" -> state.withStatusAndPhase("CANCELLED", "cancelled", event.sequence());
            case "run.suspended" -> state.withStatusAndPhase("SUSPENDED", "suspended", event.sequence());
            case "run.superseded" -> state.withStatusAndPhase("SUPERSEDED", "superseded", event.sequence());
            case "message.delta" -> state.appendAssistantDraft(text(payload, "text", ""), event.sequence());
            case "message.completed" -> state.withAssistantMessageCompleted(event.sequence());
            case "todo.created" -> state.withTodoAdded(text(payload, "todoId", ""), event.sequence());
            case "todo.updated" -> state.withLastEventSeq(event.sequence());
            case "todo.completed" -> state.withLastEventSeq(event.sequence());
            case "todo.deleted" -> state.withTodoRemoved(text(payload, "todoId", ""), event.sequence());
            case "run.completed" -> state.withStatusAndPhase("DONE", "completed", event.sequence());
            case "run.failed" -> state.withStatusAndPhase("FAILED", "failed", event.sequence());
            case "run.waiting_approval" -> state.withActiveApproval(longValue(payload, "approvalId"), event.sequence());
            default -> state.withLastEventSeq(event.sequence());
        };
    }

    private AgentRuntimeState applyToolCallWaitingApproval(AgentRuntimeState state, JsonNode payload, long sequence) {
        Long approvalId = longValue(payload, "approvalId");
        AgentRuntimeState s = approvalId != null
                ? state.withActiveApproval(approvalId, sequence)
                : state;
        return s.withToolCallWaiting(
                text(payload, "toolCallId", ""),
                state.assistantToolCallsJson(),
                state.remainingToolCalls(),
                sequence);
    }

    private String statusOrCurrent(AgentRuntimeState state, JsonNode payload) {
        return text(payload, "status", state.status());
    }

    private JsonNode readPayload(String payloadJson) {
        try {
            return OBJECT_MAPPER.readTree(payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid agent event payload JSON", ex);
        }
    }

    private String text(JsonNode payload, String fieldName, String defaultValue) {
        JsonNode value = payload.get(fieldName);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        return value.asText(defaultValue);
    }

    private Long longValue(JsonNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asLong();
    }

    private int intValue(JsonNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        return node == null || node.isNull() ? 0 : node.asInt();
    }

    private LlmTokenUsage addUsage(LlmTokenUsage current, JsonNode payload) {
        JsonNode usage = payload.get("tokenUsage");
        if (usage == null || usage.isNull()) {
            return current;
        }
        int prompt = usage.has("promptTokens") ? usage.get("promptTokens").asInt() : 0;
        int completion = usage.has("completionTokens") ? usage.get("completionTokens").asInt() : 0;
        int total = usage.has("totalTokens") ? usage.get("totalTokens").asInt() : 0;
        int cached = usage.has("cachedPromptTokens") ? usage.get("cachedPromptTokens").asInt() : 0;
        int cacheCreation = usage.has("cacheCreationPromptTokens") ? usage.get("cacheCreationPromptTokens").asInt() : 0;
        return current.add(new LlmTokenUsage(prompt, completion, total, cached, cacheCreation));
    }
}
