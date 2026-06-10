package com.penmate.backend.domain.agent.run.repository;

import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;

import java.util.List;

public interface AgentRunPendingApprovalRepository {

    int save(AgentRunPendingApproval pendingApproval);

    AgentRunPendingApproval findByApprovalId(Long approvalId);

    int markStatus(Long approvalId, String expectedStatus, String targetStatus);

    int markStatusByRunAndToolCall(Long runId, String toolCallId, String expectedStatus, String targetStatus);

    List<AgentRunPendingApproval> findStaleResumingOrApproved(int timeoutMinutes, int limit);
}
