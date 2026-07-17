package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.run.AgentRunEventPublisher;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class AgentRunPendingApprovalTimeoutGuard {

    static final int DEFAULT_TIMEOUT_MINUTES = 10;
    static final int DEFAULT_BATCH_SIZE = 100;
    static final String TIMEOUT_ERROR_MESSAGE = "Pending approval resume timed out";
    static final String TIMEOUT_ERROR_CODE = "AGENT_APPROVAL_RESUME_TIMEOUT";

    private final AgentRunPendingApprovalRepository pendingApprovalRepository;
    private final AgentRunEventPublisher eventPublisher;

    public AgentRunPendingApprovalTimeoutGuard(AgentRunPendingApprovalRepository pendingApprovalRepository,
                                               AgentRunEventPublisher eventPublisher) {
        this.pendingApprovalRepository = pendingApprovalRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void failTimedOutResumingApprovals() {
        List<AgentRunPendingApproval> pendingApprovals = pendingApprovalRepository
                .findStaleResumingOrApproved(DEFAULT_TIMEOUT_MINUTES, DEFAULT_BATCH_SIZE);
        for (AgentRunPendingApproval pendingApproval : pendingApprovals) {
            failTimedOutPendingApproval(pendingApproval);
        }
    }

    private void failTimedOutPendingApproval(AgentRunPendingApproval pendingApproval) {
        if (pendingApproval == null || pendingApproval.approvalId() == null || pendingApproval.runId() == null) {
            return;
        }
        String currentStatus = pendingApproval.pendingStatus();
        if (!"APPROVED".equalsIgnoreCase(currentStatus) && !"RESUMING".equalsIgnoreCase(currentStatus)) {
            return;
        }
        int sealed = pendingApprovalRepository.markStatus(pendingApproval.approvalId(), currentStatus, "FAILED");
        if (sealed != 1) {
            return;
        }
        eventPublisher.publish(pendingApproval.runId(), "approval.expired", timeoutPayload(pendingApproval));
        eventPublisher.publish(pendingApproval.runId(), "run.failed", timeoutPayload(pendingApproval));
        log.warn("approval resume timed out: approvalId={}, runId={}, projectId={}, toolCallId={}, traceId={}",
                pendingApproval.approvalId(),
                pendingApproval.runId(),
                pendingApproval.projectId(),
                pendingApproval.toolCallId(),
                pendingApproval.traceId());
    }

    private Map<String, Object> timeoutPayload(AgentRunPendingApproval pendingApproval) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approvalId", pendingApproval.approvalId());
        payload.put("runId", pendingApproval.runId());
        payload.put("toolCallId", pendingApproval.toolCallId());
        payload.put("toolCode", pendingApproval.toolCode());
        payload.put("errorCode", TIMEOUT_ERROR_CODE);
        payload.put("errorMessage", TIMEOUT_ERROR_MESSAGE);
        payload.put("traceId", pendingApproval.traceId());
        return payload;
    }
}
