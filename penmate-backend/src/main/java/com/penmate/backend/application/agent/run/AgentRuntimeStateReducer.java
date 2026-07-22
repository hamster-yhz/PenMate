package com.penmate.backend.application.agent.run;

import org.springframework.stereotype.Component;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;

import java.util.List;
import java.util.Map;

@Component
public class AgentRuntimeStateReducer {

    private final JsonCodec jsonCodec;

    public AgentRuntimeStateReducer(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

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
        Map<String, Object> payload = readPayload(event.payloadJson());
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

    private AgentRuntimeState applyToolCallWaitingApproval(AgentRuntimeState state,
                                                           Map<String, Object> payload,
                                                           long sequence) {
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

    private String statusOrCurrent(AgentRuntimeState state, Map<String, Object> payload) {
        return text(payload, "status", state.status());
    }

    private Map<String, Object> readPayload(String payloadJson) {
        try {
            return jsonCodec.readObject(payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid agent event payload JSON", ex);
        }
    }

    private String text(Map<String, Object> payload, String fieldName, String defaultValue) {
        Object value = payload.get(fieldName);
        if (value == null) return defaultValue;
        if (value instanceof String text) return text;
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        return "";
    }

    private Long longValue(Map<String, Object> payload, String fieldName) {
        Object value = payload.get(fieldName);
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private int intValue(Map<String, Object> payload, String fieldName) {
        Long value = longValue(payload, fieldName);
        return value == null ? 0 : value.intValue();
    }

    private LlmTokenUsage addUsage(LlmTokenUsage current, Map<String, Object> payload) {
        Object rawUsage = payload.get("tokenUsage");
        if (!(rawUsage instanceof Map<?, ?> values)) return current;
        int prompt = nestedIntValue(values, "promptTokens");
        int completion = nestedIntValue(values, "completionTokens");
        int total = nestedIntValue(values, "totalTokens");
        int cached = nestedIntValue(values, "cachedPromptTokens");
        int cacheCreation = nestedIntValue(values, "cacheCreationPromptTokens");
        return current.add(new LlmTokenUsage(prompt, completion, total, cached, cacheCreation));
    }

    private int nestedIntValue(Map<?, ?> values, String fieldName) {
        Object value = values.get(fieldName);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
