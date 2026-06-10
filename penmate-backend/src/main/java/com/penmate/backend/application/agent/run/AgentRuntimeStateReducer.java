package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;

import java.util.List;

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
            case "run.started" -> state.withStatusAndPhase("RUNNING", "preflight", event.sequence());
            case "run.phase.changed" -> state.withStatusAndPhase(statusOrCurrent(state, payload), text(payload, "phase", state.phase()), event.sequence());
            case "approval.requested", "tool.call.waiting_approval" ->
                    state.withActiveApproval(longValue(payload, "approvalId"), event.sequence());
            case "message.delta" -> state.appendAssistantDraft(text(payload, "text", ""), event.sequence());
            default -> state.withLastEventSeq(event.sequence());
        };
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
}
