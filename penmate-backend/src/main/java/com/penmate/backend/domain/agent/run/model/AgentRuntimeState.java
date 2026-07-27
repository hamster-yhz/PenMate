package com.penmate.backend.domain.agent.run.model;

import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record AgentRuntimeState(
        Long runId,
        String status,
        String phase,
        Long activeApprovalId,
        Long lastEventSeq,
        String assistantDraft,
        List<AgentLlmMessage> llmMessages,
        Integer llmTurnIndex,
        String pendingToolCallId,
        String approvedToolPayload,
        String assistantToolCallsJson,
        Integer remainingToolCalls,
        LlmTokenUsage tokenUsage,
        List<String> activeTodoProjections,
        List<Long> artifactRefs,
        boolean assistantMessageCompleted
) {

    public AgentRuntimeState {
        runId = Objects.requireNonNull(runId, "runId must not be null");
        status = requireText(status, "status");
        phase = requireText(phase, "phase");
        lastEventSeq = lastEventSeq == null ? 0L : lastEventSeq;
        assistantDraft = assistantDraft == null ? "" : assistantDraft;
        llmMessages = llmMessages == null ? List.of() : List.copyOf(llmMessages);
        llmTurnIndex = llmTurnIndex == null ? 0 : llmTurnIndex;
        pendingToolCallId = pendingToolCallId == null ? "" : pendingToolCallId;
        approvedToolPayload = approvedToolPayload == null ? "" : approvedToolPayload;
        assistantToolCallsJson = assistantToolCallsJson == null ? "[]" : assistantToolCallsJson;
        remainingToolCalls = remainingToolCalls == null ? 0 : remainingToolCalls;
        tokenUsage = tokenUsage == null ? LlmTokenUsage.ZERO : tokenUsage;
        activeTodoProjections = activeTodoProjections == null ? List.of() : List.copyOf(activeTodoProjections);
        artifactRefs = artifactRefs == null ? List.of() : List.copyOf(artifactRefs);
    }

    public static AgentRuntimeState empty(Long runId) {
        return new AgentRuntimeState(runId, "PENDING", "created", null, 0L, "", List.of(),
                0, "", "", "[]", 0, LlmTokenUsage.ZERO, List.of(), List.of(), false);
    }

    public AgentRuntimeState withStatusAndPhase(String nextStatus, String nextPhase, Long sequence) {
        return new AgentRuntimeState(runId, nextStatus, nextPhase, activeApprovalId, sequence, assistantDraft,
                llmMessages, llmTurnIndex, pendingToolCallId, approvedToolPayload, assistantToolCallsJson,
                remainingToolCalls, tokenUsage, activeTodoProjections, artifactRefs, assistantMessageCompleted);
    }

    public AgentRuntimeState withActiveApproval(Long approvalId, Long sequence) {
        return new AgentRuntimeState(runId, status, phase, approvalId, sequence, assistantDraft,
                llmMessages, llmTurnIndex, pendingToolCallId, approvedToolPayload, assistantToolCallsJson,
                remainingToolCalls, tokenUsage, activeTodoProjections, artifactRefs, assistantMessageCompleted);
    }

    public AgentRuntimeState appendAssistantDraft(String delta, Long sequence) {
        String text = delta == null ? "" : delta;
        return new AgentRuntimeState(runId, status, phase, activeApprovalId, sequence, assistantDraft + text,
                llmMessages, llmTurnIndex, pendingToolCallId, approvedToolPayload, assistantToolCallsJson,
                remainingToolCalls, tokenUsage, activeTodoProjections, artifactRefs, assistantMessageCompleted);
    }

    public AgentRuntimeState withLastEventSeq(Long sequence) {
        return new AgentRuntimeState(runId, status, phase, activeApprovalId, sequence, assistantDraft,
                llmMessages, llmTurnIndex, pendingToolCallId, approvedToolPayload, assistantToolCallsJson,
                remainingToolCalls, tokenUsage, activeTodoProjections, artifactRefs, assistantMessageCompleted);
    }

    public AgentRuntimeState withLlmTurn(int turnIndex, LlmTokenUsage usage, Long sequence) {
        return new AgentRuntimeState(runId, status, phase, activeApprovalId, sequence, assistantDraft,
                llmMessages, turnIndex, pendingToolCallId, approvedToolPayload, assistantToolCallsJson,
                remainingToolCalls, usage, activeTodoProjections, artifactRefs, assistantMessageCompleted);
    }

    public AgentRuntimeState withToolCallWaiting(String toolCallId, String toolCallsJson, int count, Long sequence) {
        return new AgentRuntimeState(runId, status, phase, activeApprovalId, sequence, assistantDraft,
                llmMessages, llmTurnIndex, toolCallId, approvedToolPayload, toolCallsJson,
                count, tokenUsage, activeTodoProjections, artifactRefs, assistantMessageCompleted);
    }

    public AgentRuntimeState withToolCallApproved(String payload, Long sequence) {
        return new AgentRuntimeState(runId, "RUNNING", "executing", null, sequence, assistantDraft,
                llmMessages, llmTurnIndex, pendingToolCallId, payload, assistantToolCallsJson,
                remainingToolCalls, tokenUsage, activeTodoProjections, artifactRefs, assistantMessageCompleted);
    }

    public AgentRuntimeState withToolCallRejected(Long sequence) {
        return new AgentRuntimeState(runId, "RUNNING", "executing", null, sequence, assistantDraft,
                llmMessages, llmTurnIndex, pendingToolCallId, approvedToolPayload, assistantToolCallsJson,
                remainingToolCalls, tokenUsage, activeTodoProjections, artifactRefs, assistantMessageCompleted);
    }

    public AgentRuntimeState withTodoAdded(String todoId, Long sequence) {
        List<String> todos = new ArrayList<>(activeTodoProjections);
        if (!todos.contains(todoId)) {
            todos.add(todoId);
        }
        return new AgentRuntimeState(runId, status, phase, activeApprovalId, sequence, assistantDraft,
                llmMessages, llmTurnIndex, pendingToolCallId, approvedToolPayload, assistantToolCallsJson,
                remainingToolCalls, tokenUsage, todos, artifactRefs, assistantMessageCompleted);
    }

    public AgentRuntimeState withTodoRemoved(String todoId, Long sequence) {
        List<String> todos = new ArrayList<>(activeTodoProjections);
        todos.remove(todoId);
        return new AgentRuntimeState(runId, status, phase, activeApprovalId, sequence, assistantDraft,
                llmMessages, llmTurnIndex, pendingToolCallId, approvedToolPayload, assistantToolCallsJson,
                remainingToolCalls, tokenUsage, todos, artifactRefs, assistantMessageCompleted);
    }

    public AgentRuntimeState withArtifactAdded(Long artifactId, Long sequence) {
        List<Long> refs = new ArrayList<>(artifactRefs);
        if (!refs.contains(artifactId)) {
            refs.add(artifactId);
        }
        return new AgentRuntimeState(runId, status, phase, activeApprovalId, sequence, assistantDraft,
                llmMessages, llmTurnIndex, pendingToolCallId, approvedToolPayload, assistantToolCallsJson,
                remainingToolCalls, tokenUsage, activeTodoProjections, refs, assistantMessageCompleted);
    }

    public AgentRuntimeState withAssistantMessageCompleted(Long sequence) {
        return new AgentRuntimeState(runId, status, phase, activeApprovalId, sequence, assistantDraft,
                llmMessages, llmTurnIndex, pendingToolCallId, approvedToolPayload, assistantToolCallsJson,
                remainingToolCalls, tokenUsage, activeTodoProjections, artifactRefs, true);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
