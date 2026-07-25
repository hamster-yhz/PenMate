package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.skill.AgentSkillActivationService;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AgentRunSuccessorService {

    private final AgentRunRepository runs;
    private final AgentSessionRepository sessions;
    private final BusinessIdGenerator ids;
    private final AgentRunEventPublisher events;
    private final AgentRunDispatchRequestPublisher dispatchRequests;
    private final AgentSkillActivationService skillActivationService;

    public AgentRunSuccessorService(AgentRunRepository runs, AgentSessionRepository sessions,
                                    BusinessIdGenerator ids, AgentRunEventPublisher events,
                                    AgentRunDispatchRequestPublisher dispatchRequests,
                                    AgentSkillActivationService skillActivationService) {
        this.runs = runs;
        this.sessions = sessions;
        this.ids = ids;
        this.events = events;
        this.dispatchRequests = dispatchRequests;
        this.skillActivationService = skillActivationService;
    }

    @Transactional
    public Long create(AgentRun predecessor, AgentRunInput oldInput, String traceId) {
        Long runId = ids.nextId();
        AgentRun successor = new AgentRun(
                runId, predecessor.projectId(), predecessor.sessionId(), predecessor.turnId(),
                predecessor.ownerUserId(), predecessor.runId(), "PENDING", "created", null, null,
                null, null, 0L, 0, null, null, null, 0L, null, traceId, null, null);
        AgentRunInput input = new AgentRunInput(
                runId, oldInput.promptSnapshot(), oldInput.taskType(), oldInput.chapterId(),
                oldInput.selectedText(), oldInput.styleSnapshotJson(), oldInput.modelSnapshotJson(),
                oldInput.pluginBindingsJson(), oldInput.inputHash());
        requireOne(runs.insert(successor), "failed to insert successor Run");
        requireOne(runs.insertInput(input), "failed to insert successor Run input");
        skillActivationService.bindSessionSkillsToRun(predecessor.sessionId(), runId);
        requireOne(sessions.rebindTurnRun(predecessor.sessionId(), predecessor.turnId(), predecessor.runId(), runId),
                "failed to bind successor Run to Turn");
        requireOne(sessions.updateLastRun(predecessor.projectId(), predecessor.sessionId(), runId),
                "failed to bind successor Run to Session");
        events.publish(runId, "run.started", Map.of(
                "phase", "created", "predecessorRunId", String.valueOf(predecessor.runId())));
        dispatchRequests.publish(new AgentRunDispatchRequested(runId, traceId));
        return runId;
    }

    private void requireOne(int affected, String message) {
        if (affected != 1) throw new IllegalStateException(message);
    }
}
