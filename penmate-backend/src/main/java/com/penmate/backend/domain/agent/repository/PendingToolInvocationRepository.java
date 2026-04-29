package com.penmate.backend.domain.agent.repository;

import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;

import java.util.List;

public interface PendingToolInvocationRepository {

    void save(PendingToolInvocationSnapshot snapshot);

    PendingToolInvocationSnapshot findByApprovalId(Long approvalId);

    int markStatus(Long approvalId, String expectedStatus, String targetStatus);

    List<PendingToolInvocationSnapshot> findStaleExecutingSnapshots(int timeoutMinutes, int limit);
}
