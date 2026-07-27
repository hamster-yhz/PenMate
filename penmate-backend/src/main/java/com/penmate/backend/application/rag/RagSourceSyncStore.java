package com.penmate.backend.application.rag;

import com.penmate.backend.domain.rag.model.RagSourceContent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class RagSourceSyncStore {
    private final JdbcTemplate jdbc;

    public RagSourceSyncStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void scheduleUpsert(Long projectId, Long ownerUserId, RagSourceContent source,
                               long quietSeconds, long maxWaitSeconds) {
        jdbc.update("""
                INSERT INTO rag_source_sync_states(
                    project_id, owner_user_id, source_type, source_id, desired_operation,
                    desired_generation, desired_source_revision, first_dirty_at, not_before, sync_status
                ) VALUES (?, ?, ?, ?, 'UPSERT', 1, ?, CURRENT_TIMESTAMP(3),
                    CURRENT_TIMESTAMP(3) + (? * INTERVAL '1 second'), 'PENDING')
                ON CONFLICT (project_id, source_type, source_id) DO UPDATE SET
                    owner_user_id = EXCLUDED.owner_user_id,
                    desired_operation = 'UPSERT',
                    desired_generation = rag_source_sync_states.desired_generation + 1,
                    desired_source_revision = EXCLUDED.desired_source_revision,
                    first_dirty_at = CASE
                        WHEN rag_source_sync_states.sync_status IN ('PENDING', 'SYNCING', 'FAILED')
                            THEN rag_source_sync_states.first_dirty_at
                        ELSE CURRENT_TIMESTAMP(3)
                    END,
                    not_before = LEAST(
                        CASE
                            WHEN rag_source_sync_states.sync_status IN ('PENDING', 'SYNCING', 'FAILED')
                                THEN rag_source_sync_states.first_dirty_at
                            ELSE CURRENT_TIMESTAMP(3)
                        END + (? * INTERVAL '1 second'),
                        CURRENT_TIMESTAMP(3) + (? * INTERVAL '1 second')
                    ),
                    sync_status = 'PENDING', lease_until = NULL, attempt_count = 0,
                    last_error_message = NULL, updated_at = CURRENT_TIMESTAMP(3)
                """, projectId, ownerUserId, source.sourceType(), source.sourceId(), source.sourceRevision(),
                quietSeconds, maxWaitSeconds, quietSeconds);
    }

    public void recordDelete(Long projectId, Long ownerUserId, String sourceType, Long sourceId) {
        jdbc.update("""
                INSERT INTO rag_source_sync_states(
                    project_id, owner_user_id, source_type, source_id, desired_operation,
                    desired_generation, first_dirty_at, not_before, sync_status
                ) VALUES (?, ?, ?, ?, 'DELETE', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 'PENDING')
                ON CONFLICT (project_id, source_type, source_id) DO UPDATE SET
                    owner_user_id = EXCLUDED.owner_user_id,
                    desired_operation = 'DELETE',
                    desired_generation = rag_source_sync_states.desired_generation + 1,
                    desired_source_revision = NULL,
                    first_dirty_at = CURRENT_TIMESTAMP(3), not_before = CURRENT_TIMESTAMP(3),
                    sync_status = 'PENDING', synced_source_revision = NULL, lease_until = NULL,
                    attempt_count = 0, last_error_message = NULL, updated_at = CURRENT_TIMESTAMP(3)
                """, projectId, ownerUserId, sourceType, sourceId);
    }

    @Transactional
    public SyncClaim claimDue() {
        List<SyncClaim> values = jdbc.query("""
                SELECT s.project_id, s.owner_user_id, s.source_type, s.source_id,
                       s.desired_operation, s.desired_generation, s.desired_source_revision
                FROM rag_source_sync_states s
                JOIN project_ai_configurations c ON c.project_id = s.project_id
                WHERE s.sync_status IN ('PENDING', 'FAILED')
                  AND s.not_before <= CURRENT_TIMESTAMP(3)
                  AND (s.lease_until IS NULL OR s.lease_until < CURRENT_TIMESTAMP(3))
                  AND c.rag_enabled = TRUE
                  AND c.active_index_build_id IS NOT NULL
                  AND c.index_status = 'READY'
                ORDER BY s.not_before, s.updated_at
                FOR UPDATE OF s SKIP LOCKED
                LIMIT 1
                """, (rs, rowNum) -> row(rs));
        if (values.isEmpty()) return null;
        SyncClaim claim = values.getFirst();
        int updated = jdbc.update("""
                UPDATE rag_source_sync_states
                SET sync_status = 'SYNCING', lease_until = CURRENT_TIMESTAMP(3) + INTERVAL '10 minutes',
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE project_id = ? AND source_type = ? AND source_id = ?
                  AND desired_generation = ?
                """, claim.projectId(), claim.sourceType(), claim.sourceId(), claim.generation());
        return updated == 1 ? claim : null;
    }

    public void complete(SyncClaim claim, String syncedRevision) {
        jdbc.update("""
                UPDATE rag_source_sync_states
                SET sync_status = 'SYNCED', synced_source_revision = ?, lease_until = NULL,
                    attempt_count = 0, last_error_message = NULL, updated_at = CURRENT_TIMESTAMP(3)
                WHERE project_id = ? AND source_type = ? AND source_id = ? AND desired_generation = ?
                """, syncedRevision, claim.projectId(), claim.sourceType(), claim.sourceId(), claim.generation());
    }

    public void requeue(SyncClaim claim, long delaySeconds) {
        jdbc.update("""
                UPDATE rag_source_sync_states
                SET sync_status = 'PENDING', lease_until = NULL,
                    not_before = CURRENT_TIMESTAMP(3) + (? * INTERVAL '1 second'),
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE project_id = ? AND source_type = ? AND source_id = ? AND desired_generation = ?
                """, delaySeconds, claim.projectId(), claim.sourceType(), claim.sourceId(), claim.generation());
    }

    public void fail(SyncClaim claim, RuntimeException exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        jdbc.update("""
                UPDATE rag_source_sync_states
                SET sync_status = 'FAILED', lease_until = NULL, attempt_count = attempt_count + 1,
                    not_before = CURRENT_TIMESTAMP(3) + (LEAST(300, 5 * power(2, LEAST(attempt_count, 6))) * INTERVAL '1 second'),
                    last_error_message = LEFT(?, 500), updated_at = CURRENT_TIMESTAMP(3)
                WHERE project_id = ? AND source_type = ? AND source_id = ? AND desired_generation = ?
                """, message, claim.projectId(), claim.sourceType(), claim.sourceId(), claim.generation());
    }

    private SyncClaim row(ResultSet rs) throws SQLException {
        return new SyncClaim(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4),
                rs.getString(5), rs.getLong(6), rs.getString(7));
    }

    public record SyncClaim(Long projectId, Long ownerUserId, String sourceType, Long sourceId,
                            String operation, long generation, String sourceRevision) { }
}
