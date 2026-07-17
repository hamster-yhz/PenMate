package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentEventArchive;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentEventArchiveMapper {

    @Select("""
            SELECT archive_id, run_id, first_sequence, last_sequence, event_count, object_key,
                   size_bytes, sha256, archive_status, verified_at, expires_at, created_at
            FROM agent_event_archives WHERE run_id = #{runId} LIMIT 1
            """)
    AgentEventArchive findByRunId(@Param("runId") Long runId);

    @Insert("""
            INSERT INTO agent_event_archives(
                archive_id, run_id, first_sequence, last_sequence, event_count, object_key,
                size_bytes, sha256, archive_status, expires_at
            ) VALUES (
                #{archiveId}, #{runId}, #{firstSequence}, #{lastSequence}, #{eventCount}, #{objectKey},
                #{sizeBytes}, #{sha256}, 'UPLOADED', #{expiresAt}
            )
            ON DUPLICATE KEY UPDATE
                first_sequence = VALUES(first_sequence), last_sequence = VALUES(last_sequence),
                event_count = VALUES(event_count), object_key = VALUES(object_key),
                size_bytes = VALUES(size_bytes), sha256 = VALUES(sha256),
                archive_status = 'UPLOADED', verified_at = NULL, expires_at = VALUES(expires_at),
                updated_at = CURRENT_TIMESTAMP(3)
            """)
    int upsertUploaded(AgentEventArchive archive);

    @Update("""
            UPDATE agent_event_archives
            SET archive_status = 'VERIFIED', verified_at = #{verifiedAt}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE archive_id = #{archiveId} AND archive_status = 'UPLOADED'
            """)
    int markVerified(@Param("archiveId") Long archiveId, @Param("verifiedAt") LocalDateTime verifiedAt);

    @Select("""
            SELECT archive_id, run_id, first_sequence, last_sequence, event_count, object_key,
                   size_bytes, sha256, archive_status, verified_at, expires_at, created_at
            FROM agent_event_archives
            WHERE archive_status = 'VERIFIED' AND expires_at <= #{now}
            ORDER BY expires_at ASC LIMIT #{limit}
            """)
    List<AgentEventArchive> findExpiredVerified(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Delete("DELETE FROM agent_event_archives WHERE archive_id = #{archiveId} AND archive_status = 'VERIFIED'")
    int delete(@Param("archiveId") Long archiveId);
}
