package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;
import com.penmate.backend.domain.agent.run.repository.AgentCheckpointRepository;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Repository;

@Repository
public class AgentCheckpointRepositoryImpl implements AgentCheckpointRepository {

    private final SqlSessionFactory sqlSessionFactory;

    public AgentCheckpointRepositoryImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    public void save(AgentCheckpoint checkpoint) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            session.getMapper(AgentCheckpointMapper.class).insert(checkpoint);
        }
    }

    @Override
    public AgentCheckpoint findLatest(Long runId) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.getMapper(AgentCheckpointMapper.class).findLatest(runId);
        }
    }
}
