package com.penmate.backend.infrastructure.persistence.ledger;

import com.penmate.backend.domain.ledger.model.ProjectLedger;
import com.penmate.backend.domain.ledger.repository.ProjectLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProjectLedgerRepositoryImpl implements ProjectLedgerRepository {
    private final ProjectLedgerMapper mapper;

    @Override public List<ProjectLedger> listByProject(Long projectId) { return mapper.listByProject(projectId); }
    @Override public ProjectLedger find(Long projectId, Long ledgerId) { return mapper.find(projectId, ledgerId); }
    @Override public int countByProject(Long projectId) { return mapper.countByProject(projectId); }
    @Override public int insert(ProjectLedger ledger) { return mapper.insert(ledger); }
    @Override public int update(Long projectId, Long ledgerId, Long expectedRevision, String title, String content) {
        return mapper.update(projectId, ledgerId, expectedRevision, title, content);
    }
    @Override public int delete(Long projectId, Long ledgerId, Long expectedRevision) {
        return mapper.delete(projectId, ledgerId, expectedRevision);
    }
    @Override public int acquireAiLease(Long projectId, Long ledgerId, Long runId, String leaseToken, java.time.Instant expiresAt) {
        return mapper.acquireAiLease(projectId, ledgerId, runId, leaseToken, expiresAt);
    }
    @Override public int updateWithAiLease(Long projectId, Long ledgerId, Long expectedRevision, String title, String content, String leaseToken) {
        return mapper.updateWithAiLease(projectId, ledgerId, expectedRevision, title, content, leaseToken);
    }
    @Override public int deleteWithAiLease(Long projectId, Long ledgerId, Long expectedRevision, String leaseToken) {
        return mapper.deleteWithAiLease(projectId, ledgerId, expectedRevision, leaseToken);
    }
    @Override public int releaseAiLease(Long projectId, Long ledgerId, String leaseToken) {
        return mapper.releaseAiLease(projectId, ledgerId, leaseToken);
    }
}
