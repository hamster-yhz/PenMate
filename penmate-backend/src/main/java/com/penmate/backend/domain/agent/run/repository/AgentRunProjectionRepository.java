package com.penmate.backend.domain.agent.run.repository;

import java.util.Map;

public interface AgentRunProjectionRepository {

    Long findLatestSequence(Long runId);

    Map<String, Object> findLatestRunForSession(Long projectId, Long sessionId);

    void updateRunState(Long runId,
                        String status,
                        String phase,
                        Long activeApprovalId,
                        Long sequence,
                        String errorCode,
                        String errorMessage);

    void appendAssistantDelta(Long runId, Long sequence, String text);

    void setCurrentAssistantMessage(Long runId, Long messageId, Long sequence);

    void upsertToolCall(Long runId,
                        String toolCallId,
                        String toolCode,
                        String toolName,
                        String status,
                        Integer iteration,
                        String argumentsPreviewJson,
                        String outputPreview,
                        Long outputArtifactId,
                        Long approvalId,
                        String errorCode,
                        String errorMessage,
                        Long sequence);

    void advanceLatestSequence(Long runId, Long sequence);
}
