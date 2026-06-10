package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentRunPendingApprovalRepositoryImpl implements AgentRunPendingApprovalRepository {

    private final AgentRunPendingApprovalMapper mapper;

    public AgentRunPendingApprovalRepositoryImpl(AgentRunPendingApprovalMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int save(AgentRunPendingApproval pendingApproval) {
        return mapper.insert(pendingApproval);
    }

    @Override
    public AgentRunPendingApproval findByApprovalId(Long approvalId) {
        return mapper.findByApprovalId(approvalId);
    }

    @Override
    public int markStatus(Long approvalId, String expectedStatus, String targetStatus) {
        return mapper.markStatus(approvalId, expectedStatus, targetStatus);
    }

    @Override
    public int markStatusByRunAndToolCall(Long runId, String toolCallId, String expectedStatus, String targetStatus) {
        return mapper.markStatusByRunAndToolCall(runId, toolCallId, expectedStatus, targetStatus);
    }

    @Override
    public List<AgentRunPendingApproval> findStaleResumingOrApproved(int timeoutMinutes, int limit) {
        return mapper.findStaleResumingOrApproved(timeoutMinutes, limit);
    }
}
