package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;
import org.springframework.stereotype.Service;

@Service
public class AgentCheckpointBoundaryService {

    private final AgentRunRecoveryService recovery;
    private final AgentCheckpointService checkpoints;

    public AgentCheckpointBoundaryService(AgentRunRecoveryService recovery, AgentCheckpointService checkpoints) {
        this.recovery = recovery;
        this.checkpoints = checkpoints;
    }

    public void checkpoint(AgentEvent event) {
        AgentRuntimeState state = recovery.recover(event.runId());
        checkpoints.checkpointIfNeeded(event, state);
    }
}
