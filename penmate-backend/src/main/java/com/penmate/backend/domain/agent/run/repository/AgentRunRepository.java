package com.penmate.backend.domain.agent.run.repository;

import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;

public interface AgentRunRepository {

    int insert(AgentRun run);

    int insertInput(AgentRunInput input);

    AgentRunInput findInput(Long runId);
}
