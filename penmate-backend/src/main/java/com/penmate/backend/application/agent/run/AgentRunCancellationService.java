package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunStatus;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

@Service
public class AgentRunCancellationService {

    private static final String ERROR_CODE = "AGENT_RUN_CANCELLED";
    private static final String DEFAULT_REASON = "Cancelled by user";

    private final AgentRunRepository runs;
    private final AgentRunPendingApprovalRepository pendingApprovals;
    private final AgentRunEventPublisher events;

    public AgentRunCancellationService(AgentRunRepository runs,
                                       AgentRunPendingApprovalRepository pendingApprovals,
                                       AgentRunEventPublisher events) {
        this.runs = runs;
        this.pendingApprovals = pendingApprovals;
        this.events = events;
    }

    @Transactional
    public AgentRun cancel(Long projectId, Long runId, Long operatorId, String reason) {
        AgentRun current = requireOwnedRun(projectId, runId, operatorId);
        if (current.status() == AgentRunStatus.CANCELLED) return current;
        if (current.status().isTerminal()) {
            throw BusinessException.conflict("Terminal Agent Run cannot be cancelled");
        }

        String resolvedReason = normalizeReason(reason);
        if (!runs.cancelRecoverable(runId, ERROR_CODE, resolvedReason)) {
            AgentRun raced = requireOwnedRun(projectId, runId, operatorId);
            if (raced.status() == AgentRunStatus.CANCELLED) return raced;
            throw BusinessException.conflict("Agent Run is no longer cancellable");
        }
        pendingApprovals.invalidateOpenByRunId(runId);
        events.publish(runId, "run.cancelled", Map.of(
                "errorCode", ERROR_CODE,
                "errorMessage", resolvedReason,
                "operatorId", String.valueOf(operatorId)));
        return Objects.requireNonNull(runs.findRun(runId), "Cancelled Agent Run disappeared");
    }

    private AgentRun requireOwnedRun(Long projectId, Long runId, Long operatorId) {
        AgentRun run = runs.findRun(runId);
        if (run == null || !Objects.equals(run.projectId(), projectId)) {
            throw BusinessException.notFound("Agent Run not found");
        }
        if (!Objects.equals(run.ownerUserId(), operatorId)) {
            throw BusinessException.forbidden("Agent Run belongs to another user");
        }
        return run;
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) return DEFAULT_REASON;
        String normalized = reason.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}
