package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.repository.AgentRunProjectionRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class AgentRunProjectionRepositoryImpl implements AgentRunProjectionRepository {

    private final AgentRunProjectionMapper mapper;

    public AgentRunProjectionRepositoryImpl(AgentRunProjectionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long findLatestSequence(Long runId) {
        return mapper.findLatestSequence(runId);
    }

    @Override
    public Map<String, Object> findLatestRunForSession(Long projectId, Long sessionId) {
        return mapper.findLatestRunForSession(projectId, sessionId);
    }

    @Override
    public void updateRunState(Long runId, String status, String phase, Long activeApprovalId, Long sequence, String errorCode, String errorMessage) {
        mapper.upsertRunState(runId, status, phase, activeApprovalId, sequence, errorCode, errorMessage);
    }

    @Override
    public void appendAssistantDelta(Long runId, Long sequence, String text) {
        mapper.appendAssistantDelta(runId, sequence, text == null ? "" : text);
    }

    @Override
    public void setCurrentAssistantMessage(Long runId, Long messageId, Long sequence) {
        mapper.setCurrentAssistantMessage(runId, messageId, sequence);
    }

    @Override
    public void upsertToolCall(Long runId,
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
                               Long sequence) {
        mapper.upsertToolCall(runId, toolCallId, toolCode, toolName, status, iteration, argumentsPreviewJson,
                outputPreview, outputArtifactId, approvalId, errorCode, errorMessage);
        mapper.upsertRunState(runId, null, null, null, sequence, null, null);
    }

    @Override
    public void advanceLatestSequence(Long runId, Long sequence) {
        mapper.upsertRunState(runId, null, null, null, sequence, null, null);
    }
}
