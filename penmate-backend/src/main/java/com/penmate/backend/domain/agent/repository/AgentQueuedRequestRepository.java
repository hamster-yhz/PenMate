package com.penmate.backend.domain.agent.repository;

import com.penmate.backend.domain.agent.model.AgentQueuedRequest;

public interface AgentQueuedRequestRepository {
    AgentQueuedRequest findOpen(Long projectId, Long sessionId);

    int insert(AgentQueuedRequest request);

    int withdraw(Long projectId, Long sessionId, Long requestId, Long ownerUserId);

    AgentQueuedRequest claimNextIdle();

    int markCompleted(Long requestId);

    int requeue(Long requestId, String error);

    int markFailed(Long requestId, String error);
}
