package com.penmate.backend.infrastructure.persistence.ledger;

import com.penmate.backend.domain.ledger.model.ProjectLedger;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProjectLedgerMapper {
    String COLUMNS = "ledger_id AS ledgerId, project_id AS projectId, title, content, "
            + "content_revision AS contentRevision, lease_owner_type AS leaseOwnerType, "
            + "lease_owner_id AS leaseOwnerId, lease_token AS leaseToken, lease_expires_at AS leaseExpiresAt, "
            + "created_at AS createdAt, updated_at AS updatedAt";

    @Select("SELECT ledger_id AS ledgerId, project_id AS projectId, title, '' AS content, "
            + "content_revision AS contentRevision, lease_owner_type AS leaseOwnerType, "
            + "lease_owner_id AS leaseOwnerId, lease_token AS leaseToken, lease_expires_at AS leaseExpiresAt, "
            + "created_at AS createdAt, updated_at AS updatedAt "
            + "FROM project_ledgers WHERE project_id = #{projectId} ORDER BY updated_at DESC, ledger_id")
    List<ProjectLedger> listByProject(Long projectId);

    @Select("SELECT " + COLUMNS + " FROM project_ledgers WHERE project_id = #{projectId} AND ledger_id = #{ledgerId}")
    ProjectLedger find(@Param("projectId") Long projectId, @Param("ledgerId") Long ledgerId);

    @Select("SELECT COUNT(*) FROM project_ledgers WHERE project_id = #{projectId}")
    int countByProject(Long projectId);

    @Insert("""
            INSERT INTO project_ledgers(ledger_id, project_id, title, content, content_revision)
            VALUES(#{ledgerId}, #{projectId}, #{title}, #{content}, #{contentRevision})
            """)
    int insert(ProjectLedger ledger);

    @Update("""
            UPDATE project_ledgers
            SET title = #{title}, content = #{content}, content_revision = content_revision + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND ledger_id = #{ledgerId}
              AND content_revision = #{expectedRevision}
              AND (lease_owner_type IS NULL OR lease_expires_at <= CURRENT_TIMESTAMP(3))
            """)
    int update(@Param("projectId") Long projectId, @Param("ledgerId") Long ledgerId,
               @Param("expectedRevision") Long expectedRevision, @Param("title") String title,
               @Param("content") String content);

    @Delete("""
            DELETE FROM project_ledgers
            WHERE project_id = #{projectId} AND ledger_id = #{ledgerId}
              AND content_revision = #{expectedRevision}
              AND (lease_owner_type IS NULL OR lease_expires_at <= CURRENT_TIMESTAMP(3))
            """)
    int delete(@Param("projectId") Long projectId, @Param("ledgerId") Long ledgerId,
               @Param("expectedRevision") Long expectedRevision);

    @Update("""
            UPDATE project_ledgers
            SET lease_owner_type = 'AI', lease_owner_id = #{runId}, lease_token = #{leaseToken},
                lease_expires_at = #{expiresAt}
            WHERE project_id = #{projectId} AND ledger_id = #{ledgerId}
              AND (lease_owner_type IS NULL OR lease_expires_at <= CURRENT_TIMESTAMP(3))
            """)
    int acquireAiLease(@Param("projectId") Long projectId, @Param("ledgerId") Long ledgerId,
                       @Param("runId") Long runId, @Param("leaseToken") String leaseToken,
                       @Param("expiresAt") java.time.Instant expiresAt);

    @Update("""
            UPDATE project_ledgers
            SET title = #{title}, content = #{content}, content_revision = content_revision + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND ledger_id = #{ledgerId}
              AND content_revision = #{expectedRevision} AND lease_owner_type = 'AI'
              AND lease_token = #{leaseToken} AND lease_expires_at > CURRENT_TIMESTAMP(3)
            """)
    int updateWithAiLease(@Param("projectId") Long projectId, @Param("ledgerId") Long ledgerId,
                          @Param("expectedRevision") Long expectedRevision, @Param("title") String title,
                          @Param("content") String content, @Param("leaseToken") String leaseToken);

    @Delete("""
            DELETE FROM project_ledgers
            WHERE project_id = #{projectId} AND ledger_id = #{ledgerId}
              AND content_revision = #{expectedRevision} AND lease_owner_type = 'AI'
              AND lease_token = #{leaseToken} AND lease_expires_at > CURRENT_TIMESTAMP(3)
            """)
    int deleteWithAiLease(@Param("projectId") Long projectId, @Param("ledgerId") Long ledgerId,
                          @Param("expectedRevision") Long expectedRevision, @Param("leaseToken") String leaseToken);

    @Update("""
            UPDATE project_ledgers
            SET lease_owner_type = NULL, lease_owner_id = NULL, lease_token = NULL, lease_expires_at = NULL
            WHERE project_id = #{projectId} AND ledger_id = #{ledgerId}
              AND lease_owner_type = 'AI' AND lease_token = #{leaseToken}
            """)
    int releaseAiLease(@Param("projectId") Long projectId, @Param("ledgerId") Long ledgerId,
                       @Param("leaseToken") String leaseToken);
}
