package com.penmate.backend.infrastructure.persistence.ops;

import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.model.OpsMigrationTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * OpsMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface OpsMapper {

    @Select("""
            SELECT id, job_type, biz_key, status, error_msg, started_at, finished_at, created_at, updated_at
            FROM ops_async_jobs
            WHERE id = #{jobId}
            LIMIT 1
            """)
    OpsAsyncJob findJobById(@Param("jobId") Long jobId);

    @Select("""
            SELECT id, job_type, biz_key, status, error_msg, started_at, finished_at, created_at, updated_at
            FROM ops_async_jobs
            WHERE (#{bizKey} IS NULL OR #{bizKey} = '' OR biz_key = #{bizKey})
              AND (#{jobType} IS NULL OR #{jobType} = '' OR job_type = #{jobType})
            ORDER BY id DESC
            LIMIT 100
            """)
    List<OpsAsyncJob> listJobs(@Param("bizKey") String bizKey,
                               @Param("jobType") String jobType);

    @Insert("""
            INSERT INTO ops_async_jobs(job_type, biz_key, status, error_msg, started_at, finished_at)
            VALUES (#{jobType}, #{bizKey}, #{status}, #{errorMsg}, #{startedAt}, #{finishedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertJob(OpsAsyncJob job);

    @Update("""
            UPDATE ops_async_jobs
            SET status = #{status},
                error_msg = #{errorMsg},
                started_at = CASE WHEN #{status} = 'running' THEN COALESCE(started_at, CURRENT_TIMESTAMP(3)) ELSE started_at END,
                finished_at = CASE WHEN #{status} IN ('done', 'failed') THEN CURRENT_TIMESTAMP(3) ELSE finished_at END,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{jobId}
            """)
    int updateJobStatus(@Param("jobId") Long jobId,
                        @Param("status") String status,
                        @Param("errorMsg") String errorMsg);

    @Insert("""
            INSERT INTO ops_migrations(migration_type, status, progress_pct, summary_json, error_msg, started_at, finished_at)
            VALUES (#{migrationType}, #{status}, #{progressPct}, #{summaryJson}, #{errorMsg}, #{startedAt}, #{finishedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMigration(OpsMigrationTask task);

    @Select("""
            SELECT id, migration_type, status, progress_pct, summary_json, error_msg, started_at, finished_at, created_at, updated_at
            FROM ops_migrations
            WHERE id = #{migrationId}
            LIMIT 1
            """)
    OpsMigrationTask findMigrationById(@Param("migrationId") Long migrationId);

    @Update("""
            UPDATE ops_migrations
            SET status = #{status},
                progress_pct = #{progressPct},
                summary_json = #{summaryJson},
                error_msg = #{errorMsg},
                finished_at = CASE WHEN #{status} IN ('done', 'failed') THEN CURRENT_TIMESTAMP(3) ELSE finished_at END,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{migrationId}
            """)
    int updateMigration(@Param("migrationId") Long migrationId,
                        @Param("status") String status,
                        @Param("progressPct") Integer progressPct,
                        @Param("summaryJson") String summaryJson,
                        @Param("errorMsg") String errorMsg);
}

