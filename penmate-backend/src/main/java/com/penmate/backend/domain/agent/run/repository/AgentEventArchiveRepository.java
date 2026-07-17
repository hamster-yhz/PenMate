package com.penmate.backend.domain.agent.run.repository;

import com.penmate.backend.domain.agent.run.model.AgentEventArchive;

import java.time.LocalDateTime;
import java.util.List;

public interface AgentEventArchiveRepository {
    AgentEventArchive findByRunId(Long runId);
    int upsertUploaded(AgentEventArchive archive);
    int markVerified(Long archiveId, LocalDateTime verifiedAt);
    List<AgentEventArchive> findExpiredVerified(LocalDateTime now, int limit);
    int delete(Long archiveId);
}
