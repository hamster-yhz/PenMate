package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.model.AgentRunLease;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AgentRunStateTransitionService {

    private final AgentRunLeaseService leases;
    private final AgentRunEventPublisher events;
    private final AgentRunPendingApprovalRepository pendingApprovals;
    private final AgentRunSuccessorService successors;
    private final AgentRunOutputEventService outputs;

    public AgentRunStateTransitionService(AgentRunLeaseService leases,
                                          AgentRunEventPublisher events,
                                          AgentRunPendingApprovalRepository pendingApprovals,
                                          AgentRunSuccessorService successors,
                                          AgentRunOutputEventService outputs) {
        this.leases = leases;
        this.events = events;
        this.pendingApprovals = pendingApprovals;
        this.successors = successors;
        this.outputs = outputs;
    }

    @Transactional
    public Outcome waitingApproval(AgentRunLease lease, Long approvalId, Long completedApprovalId) {
        leases.waitingApproval(lease, approvalId);
        completeApproval(completedApprovalId);
        return new Outcome(null, events.publish(lease.runId(), "run.waiting_approval",
                Map.of("approvalId", approvalId)));
    }

    @Transactional
    public Outcome failed(AgentRunLease lease, String assistantText, Long completedApprovalId) {
        leases.failTerminal(lease, "AGENT_RUN_FAILED", assistantText);
        completeApproval(completedApprovalId);
        AgentEvent interrupted = outputs.persistInterrupted(lease.runId(), assistantText);
        return new Outcome(interrupted, events.publish(lease.runId(), "run.failed",
                Map.of("phase", "failed", "message", assistantText)));
    }

    @Transactional
    public Outcome completed(AgentRunLease lease, String assistantText, LlmTokenUsage tokenUsage,
                             boolean assistantMessageCompleted, Long completedApprovalId) {
        leases.complete(lease);
        completeApproval(completedApprovalId);
        AgentEvent message = assistantMessageCompleted ? null : events.publish(lease.runId(), "message.completed",
                Map.of("channel", "final", "role", "assistant", "text", assistantText));
        AgentEvent terminal = events.publish(lease.runId(), "run.completed",
                Map.of("phase", "completed", "tokenUsage", tokenUsage));
        return new Outcome(message, terminal);
    }

    @Transactional
    public Long supersede(AgentRunLease lease, AgentRun run, AgentRunInput input,
                          String traceId, List<String> changedFields) {
        String fields = String.join(",", changedFields);
        leases.supersede(lease, "Run dependencies changed: " + fields);
        pendingApprovals.invalidateOpenByRunId(run.runId());
        outputs.persistInterrupted(run.runId());
        events.publish(run.runId(), "run.superseded", Map.of(
                "errorCode", "AGENT_RUN_DEPENDENCY_CHANGED",
                "errorMessage", "Run dependencies changed",
                "changedFields", changedFields));
        return successors.create(run, input, traceId);
    }

    private void completeApproval(Long approvalId) {
        if (approvalId == null) return;
        if (pendingApprovals.markStatus(approvalId, "APPROVED", "COMPLETED") != 1
                && pendingApprovals.markStatus(approvalId, "REJECTED", "COMPLETED") != 1) {
            throw new IllegalStateException("Reviewed Agent tool continuation is no longer current");
        }
    }

    public record Outcome(AgentEvent messageEvent, AgentEvent stateEvent) {
    }
}
