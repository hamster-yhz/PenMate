package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;
import com.penmate.backend.domain.agent.run.repository.AgentCheckpointRepository;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

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

    @Override
    public List<AgentCheckpoint> findLatest(Long runId, int limit) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.getMapper(AgentCheckpointMapper.class).findLatestLimit(runId, limit);
        }
    }

    @Override
    public int deleteOlderThanLatest(Long runId, int keep) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.getMapper(AgentCheckpointMapper.class).deleteOlderThanLatest(runId, keep);
        }
    }

    @Override
    public int deleteTerminalOlderThan(LocalDateTime cutoff) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.getMapper(AgentCheckpointMapper.class).deleteTerminalOlderThan(cutoff);
        }
    }
}
