package com.penmate.backend.domain.agent.run.model;

import com.penmate.backend.domain.agent.model.AgentLlmMessage;

import java.util.List;
import java.util.Objects;

public record AgentRunContinuation(
        int schemaVersion,
        Long runId,
        String continuationStage,
        List<AgentLlmMessage> messages,
        int llmTurnIndex,
        int iterationIndex,
        int nextToolCallIndex,
        String assistantText,
        LlmTokenUsage tokenUsage,
        AgentRunNoProgressState noProgressState
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public AgentRunContinuation {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Agent Run continuation schema: " + schemaVersion);
        }
        runId = Objects.requireNonNull(runId, "runId must not be null");
        AgentRunContinuationStage stage = AgentRunContinuationStage.from(continuationStage);
        continuationStage = stage.name();
        messages = List.copyOf(messages == null ? List.of() : messages);
        if (llmTurnIndex < 1) throw new IllegalArgumentException("llmTurnIndex must be positive");
        if (iterationIndex < 0) throw new IllegalArgumentException("iterationIndex must not be negative");
        if (nextToolCallIndex < 0) throw new IllegalArgumentException("nextToolCallIndex must not be negative");
        if (stage != AgentRunContinuationStage.READY_FOR_TOOL && nextToolCallIndex != 0) {
            throw new IllegalArgumentException("nextToolCallIndex is only valid for READY_FOR_TOOL");
        }
        assistantText = assistantText == null ? "" : assistantText;
        tokenUsage = tokenUsage == null ? LlmTokenUsage.ZERO : tokenUsage;
        noProgressState = noProgressState == null ? AgentRunNoProgressState.EMPTY : noProgressState;
    }

    public AgentRunContinuationStage stage() {
        return AgentRunContinuationStage.from(continuationStage);
    }

    public static AgentRunContinuation readyForLlm(Long runId, List<AgentLlmMessage> messages,
                                                    int llmTurnIndex, int iterationIndex,
                                                    String assistantText, LlmTokenUsage tokenUsage) {
        return readyForLlm(runId, messages, llmTurnIndex, iterationIndex, assistantText, tokenUsage,
                AgentRunNoProgressState.EMPTY);
    }

    public static AgentRunContinuation readyForLlm(Long runId, List<AgentLlmMessage> messages,
                                                    int llmTurnIndex, int iterationIndex,
                                                    String assistantText, LlmTokenUsage tokenUsage,
                                                    AgentRunNoProgressState noProgressState) {
        return new AgentRunContinuation(CURRENT_SCHEMA_VERSION, runId,
                AgentRunContinuationStage.READY_FOR_LLM.name(), messages, llmTurnIndex,
                iterationIndex, 0, assistantText, tokenUsage, noProgressState);
    }

    public static AgentRunContinuation readyForTool(Long runId, List<AgentLlmMessage> messages,
                                                     int llmTurnIndex, int iterationIndex,
                                                     int nextToolCallIndex, String assistantText,
                                                     LlmTokenUsage tokenUsage) {
        return readyForTool(runId, messages, llmTurnIndex, iterationIndex, nextToolCallIndex,
                assistantText, tokenUsage, AgentRunNoProgressState.EMPTY);
    }

    public static AgentRunContinuation readyForTool(Long runId, List<AgentLlmMessage> messages,
                                                     int llmTurnIndex, int iterationIndex,
                                                     int nextToolCallIndex, String assistantText,
                                                     LlmTokenUsage tokenUsage,
                                                     AgentRunNoProgressState noProgressState) {
        return new AgentRunContinuation(CURRENT_SCHEMA_VERSION, runId,
                AgentRunContinuationStage.READY_FOR_TOOL.name(), messages, llmTurnIndex,
                iterationIndex, nextToolCallIndex, assistantText, tokenUsage, noProgressState);
    }

    public static AgentRunContinuation completed(Long runId, List<AgentLlmMessage> messages,
                                                  int llmTurnIndex, int iterationIndex,
                                                  String assistantText, LlmTokenUsage tokenUsage) {
        return completed(runId, messages, llmTurnIndex, iterationIndex, assistantText, tokenUsage,
                AgentRunNoProgressState.EMPTY);
    }

    public static AgentRunContinuation completed(Long runId, List<AgentLlmMessage> messages,
                                                  int llmTurnIndex, int iterationIndex,
                                                  String assistantText, LlmTokenUsage tokenUsage,
                                                  AgentRunNoProgressState noProgressState) {
        return new AgentRunContinuation(CURRENT_SCHEMA_VERSION, runId,
                AgentRunContinuationStage.COMPLETED.name(), messages, llmTurnIndex,
                iterationIndex, 0, assistantText, tokenUsage, noProgressState);
    }
}
