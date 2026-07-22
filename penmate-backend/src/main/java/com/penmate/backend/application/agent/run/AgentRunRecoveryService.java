package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;
import com.penmate.backend.domain.agent.run.repository.AgentRunEventRepository;
import org.springframework.stereotype.Service;

@Service
public class AgentRunRecoveryService {

    private final AgentCheckpointService checkpointService;
    private final AgentRunEventRepository eventRepository;
    private final AgentRuntimeStateReducer stateReducer;

    public AgentRunRecoveryService(AgentCheckpointService checkpointService,
                                   AgentRunEventRepository eventRepository,
                                   AgentRuntimeStateReducer stateReducer) {
        this.checkpointService = checkpointService;
        this.eventRepository = eventRepository;
        this.stateReducer = stateReducer;
    }

    public AgentRuntimeState recover(Long runId) {
        AgentRuntimeState checkpoint = checkpointService.loadLatest(runId);
        if (checkpoint == null) checkpoint = AgentRuntimeState.empty(runId);
        return stateReducer.applyAll(
                checkpoint,
                eventRepository.listAfter(runId, checkpoint.lastEventSeq())
        );
    }
}
