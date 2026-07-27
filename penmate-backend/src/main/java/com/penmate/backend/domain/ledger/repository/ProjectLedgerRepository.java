package com.penmate.backend.domain.ledger.repository;

import com.penmate.backend.domain.ledger.model.ProjectLedger;

import java.util.List;

public interface ProjectLedgerRepository {
    List<ProjectLedger> listByProject(Long projectId);
    ProjectLedger find(Long projectId, Long ledgerId);
    int countByProject(Long projectId);
    int insert(ProjectLedger ledger);
    int update(Long projectId, Long ledgerId, Long expectedRevision, String title, String content);
    int delete(Long projectId, Long ledgerId, Long expectedRevision);
    int acquireAiLease(Long projectId, Long ledgerId, Long runId, String leaseToken, java.time.Instant expiresAt);
    int updateWithAiLease(Long projectId, Long ledgerId, Long expectedRevision, String title, String content, String leaseToken);
    int deleteWithAiLease(Long projectId, Long ledgerId, Long expectedRevision, String leaseToken);
    int releaseAiLease(Long projectId, Long ledgerId, String leaseToken);
}
