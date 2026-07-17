package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class AgentRunRetryService {

    private final AgentRunRepository runs;
    private final AgentRunSuccessorService successors;

    public AgentRunRetryService(AgentRunRepository runs, AgentRunSuccessorService successors) {
        this.runs = runs;
        this.successors = successors;
    }

    @Transactional
    public AgentRun retry(Long projectId, Long runId, Long operatorId, String traceId) {
        AgentRun predecessor = requireOwnedRunForUpdate(projectId, runId, operatorId);
        if (!predecessor.status().isTerminal()) {
            throw BusinessException.conflict("Only terminal Agent Run can be retried");
        }

        AgentRun existing = runs.findSuccessor(predecessor.runId());
        if (existing != null) return existing;

        AgentRunInput input = runs.findInput(predecessor.runId());
        if (input == null) {
            throw new IllegalStateException("Terminal Agent Run input is unavailable");
        }
        String successorTraceId = traceId == null || traceId.isBlank() ? predecessor.traceId() : traceId.trim();
        Long successorId = successors.create(predecessor, input, successorTraceId);
        return Objects.requireNonNull(runs.findRun(successorId), "Created successor Agent Run disappeared");
    }

    private AgentRun requireOwnedRunForUpdate(Long projectId, Long runId, Long operatorId) {
        AgentRun run = runs.findRunForUpdate(runId);
        if (run == null || !Objects.equals(run.projectId(), projectId)) {
            throw BusinessException.notFound("Agent Run not found");
        }
        if (!Objects.equals(run.ownerUserId(), operatorId)) {
            throw BusinessException.forbidden("Agent Run belongs to another user");
        }
        return run;
    }
}
