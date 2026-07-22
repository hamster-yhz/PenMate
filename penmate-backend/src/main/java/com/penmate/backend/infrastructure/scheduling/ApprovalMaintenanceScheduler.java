package com.penmate.backend.infrastructure.scheduling;

import com.penmate.backend.application.approval.AgentRunPendingApprovalTimeoutGuard;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ApprovalMaintenanceScheduler {

    private final AgentRunPendingApprovalTimeoutGuard timeoutGuard;

    public ApprovalMaintenanceScheduler(AgentRunPendingApprovalTimeoutGuard timeoutGuard) {
        this.timeoutGuard = timeoutGuard;
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void expireTimedOutApprovals() {
        timeoutGuard.failTimedOutResumingApprovals();
    }
}
