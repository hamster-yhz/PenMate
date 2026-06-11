package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.springframework.stereotype.Repository;

@Repository
public class AgentRunRepositoryImpl implements AgentRunRepository {

    private final AgentRunMapper agentRunMapper;

    public AgentRunRepositoryImpl(AgentRunMapper agentRunMapper) {
        this.agentRunMapper = agentRunMapper;
    }

    @Override
    public int insert(AgentRun run) {
        return agentRunMapper.insert(run);
    }

    @Override
    public int insertInput(AgentRunInput input) {
        return agentRunMapper.insertInput(input);
    }

    @Override
    public AgentRunInput findInput(Long runId) {
        return agentRunMapper.findInput(runId);
    }

    @Override
    public AgentRun findRun(Long runId) {
        return agentRunMapper.findRun(runId);
    }
}
