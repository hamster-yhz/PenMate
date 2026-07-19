package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentEventArchive;
import com.penmate.backend.domain.agent.run.repository.AgentEventArchiveRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class AgentEventArchiveRepositoryImpl implements AgentEventArchiveRepository {
    private final AgentEventArchiveMapper mapper;

    public AgentEventArchiveRepositoryImpl(AgentEventArchiveMapper mapper) {
        this.mapper = mapper;
    }

    @Override public AgentEventArchive findByRunId(Long runId) { return mapper.findByRunId(runId); }
    @Override public int upsertUploaded(AgentEventArchive archive) { return mapper.upsertUploaded(archive); }
    @Override public int markVerified(Long archiveId, Instant verifiedAt) { return mapper.markVerified(archiveId, verifiedAt); }
    @Override public List<AgentEventArchive> findExpiredVerified(Instant now, int limit) { return mapper.findExpiredVerified(now, limit); }
    @Override public int delete(Long archiveId) { return mapper.delete(archiveId); }
}
