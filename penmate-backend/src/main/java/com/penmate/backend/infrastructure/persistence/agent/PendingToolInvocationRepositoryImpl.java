package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PendingToolInvocationRepositoryImpl implements PendingToolInvocationRepository {

    private final PendingToolInvocationMapper pendingToolInvocationMapper;

    public PendingToolInvocationRepositoryImpl(PendingToolInvocationMapper pendingToolInvocationMapper) {
        this.pendingToolInvocationMapper = pendingToolInvocationMapper;
    }

    @Override
    public void save(PendingToolInvocationSnapshot snapshot) {
        pendingToolInvocationMapper.insert(snapshot);
    }

    @Override
    public PendingToolInvocationSnapshot findByApprovalId(Long approvalId) {
        return pendingToolInvocationMapper.findByApprovalId(approvalId);
    }

    @Override
    public int markStatus(Long approvalId, String expectedStatus, String targetStatus) {
        return pendingToolInvocationMapper.markStatus(approvalId, expectedStatus, targetStatus);
    }

    @Override
    public List<PendingToolInvocationSnapshot> findStaleExecutingSnapshots(int timeoutMinutes, int limit) {
        return pendingToolInvocationMapper.findStaleExecutingSnapshots(timeoutMinutes, limit);
    }
}
