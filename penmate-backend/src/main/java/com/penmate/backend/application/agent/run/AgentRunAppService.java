package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

@Service
public class AgentRunAppService {

    private final AgentRunRepository agentRunRepository;
    private final AgentRunEventPublisher eventPublisher;
    private final AgentRunDispatcher runDispatcher;

    public AgentRunAppService(AgentRunRepository agentRunRepository,
                              AgentRunEventPublisher eventPublisher,
                              AgentRunDispatcher runDispatcher) {
        this.agentRunRepository = agentRunRepository;
        this.eventPublisher = eventPublisher;
        this.runDispatcher = runDispatcher;
    }

    public AgentRunResult createRun(AgentRunCommand command) {
        AgentRun run = new AgentRun(
                command.runId(),
                command.projectId(),
                command.sessionId(),
                command.turnId(),
                command.ownerUserId(),
                "PENDING",
                "created",
                null,
                null,
                0L,
                null,
                command.traceId(),
                null,
                null
        );
        AgentRunInput input = new AgentRunInput(
                command.runId(),
                command.promptSnapshot(),
                command.taskType(),
                command.chapterId(),
                command.selectedText(),
                command.styleSnapshotJson(),
                command.modelSnapshotJson(),
                command.pluginBindingsJson(),
                command.inputHash()
        );
        requireOne(agentRunRepository.insert(run), "failed to insert agent run");
        requireOne(agentRunRepository.insertInput(input), "failed to insert agent run input");
        AgentEvent started = eventPublisher.publish(command.runId(), "run.started", Map.of("phase", "created"));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runDispatcher.dispatchInitialRun(command.runId(), command.traceId());
                }
            });
        } else {
            runDispatcher.dispatchInitialRun(command.runId(), command.traceId());
        }
        return new AgentRunResult(command.runId(), "running", "created", started.sequence());
    }

    private void requireOne(int affected, String message) {
        if (affected != 1) {
            throw new IllegalStateException(message);
        }
    }
}
