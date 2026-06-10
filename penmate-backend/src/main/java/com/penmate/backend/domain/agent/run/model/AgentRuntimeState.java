package com.penmate.backend.domain.agent.run.model;

import com.penmate.backend.domain.agent.model.AgentLlmMessage;

import java.util.List;
import java.util.Objects;

public record AgentRuntimeState(
        Long runId,
        String status,
        String phase,
        Long activeApprovalId,
        Long lastEventSeq,
        String assistantDraft,
        List<AgentLlmMessage> llmMessages
) {

    public AgentRuntimeState {
        runId = Objects.requireNonNull(runId, "runId must not be null");
        status = requireText(status, "status");
        phase = requireText(phase, "phase");
        lastEventSeq = lastEventSeq == null ? 0L : lastEventSeq;
        assistantDraft = assistantDraft == null ? "" : assistantDraft;
        llmMessages = llmMessages == null ? List.of() : List.copyOf(llmMessages);
    }

    public static AgentRuntimeState empty(Long runId) {
        return new AgentRuntimeState(runId, "PENDING", "created", null, 0L, "", List.of());
    }

    public AgentRuntimeState withStatusAndPhase(String nextStatus, String nextPhase, Long sequence) {
        return new AgentRuntimeState(runId, nextStatus, nextPhase, activeApprovalId, sequence, assistantDraft, llmMessages);
    }

    public AgentRuntimeState withActiveApproval(Long approvalId, Long sequence) {
        return new AgentRuntimeState(runId, status, phase, approvalId, sequence, assistantDraft, llmMessages);
    }

    public AgentRuntimeState appendAssistantDraft(String delta, Long sequence) {
        String text = delta == null ? "" : delta;
        return new AgentRuntimeState(runId, status, phase, activeApprovalId, sequence, assistantDraft + text, llmMessages);
    }

    public AgentRuntimeState withLastEventSeq(Long sequence) {
        return new AgentRuntimeState(runId, status, phase, activeApprovalId, sequence, assistantDraft, llmMessages);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
