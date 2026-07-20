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

@Mapper
public interface OpsMapper {

    String JOB_COLUMNS = """
            id, job_id, job_type, biz_key, owner_user_id, project_id,
            CAST(payload_json AS TEXT) AS payload_json, CAST(result_json AS TEXT) AS result_json,
            status, attempt_count, max_attempts, scheduled_at, lease_owner, lease_until,
            heartbeat_at, cancel_requested_at, progress_current, progress_total, progress_message,
            last_error_code, last_error_message, started_at, finished_at, created_at, updated_at
            """;

    String QUALIFIED_JOB_COLUMNS = """
            job.id, job.job_id, job.job_type, job.biz_key, job.owner_user_id, job.project_id,
            CAST(job.payload_json AS TEXT) AS payload_json, CAST(job.result_json AS TEXT) AS result_json,
            job.status, job.attempt_count, job.max_attempts, job.scheduled_at, job.lease_owner, job.lease_until,
            job.heartbeat_at, job.cancel_requested_at, job.progress_current, job.progress_total, job.progress_message,
            job.last_error_code, job.last_error_message, job.started_at, job.finished_at, job.created_at, job.updated_at
            """;

    @Select("SELECT " + JOB_COLUMNS + " FROM ops_async_jobs WHERE job_id = #{jobId} LIMIT 1")
    OpsAsyncJob findJobById(Long jobId);

    @Select("SELECT " + JOB_COLUMNS + " FROM ops_async_jobs WHERE biz_key = #{bizKey} LIMIT 1")
    OpsAsyncJob findJobByBizKey(String bizKey);

    @Select("""
            SELECT """ + JOB_COLUMNS + """
            FROM ops_async_jobs
            WHERE (#{bizKey,jdbcType=VARCHAR} IS NULL OR #{bizKey,jdbcType=VARCHAR} = '' OR biz_key = #{bizKey})
              AND (#{jobType,jdbcType=VARCHAR} IS NULL OR #{jobType,jdbcType=VARCHAR} = '' OR job_type = #{jobType})
            ORDER BY created_at DESC, id DESC
            LIMIT 100
            """)
    List<OpsAsyncJob> listJobs(@Param("bizKey") String bizKey, @Param("jobType") String jobType);

    @Insert("""
            INSERT INTO ops_async_jobs(
                job_id, job_type, biz_key, owner_user_id, project_id, payload_json,
                status, attempt_count, max_attempts, scheduled_at,
                progress_current, progress_total
            ) VALUES (
                #{jobId}, #{jobType}, #{bizKey}, #{ownerUserId}, #{projectId},
                #{payloadJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler},
                #{status}, #{attemptCount}, #{maxAttempts}, #{scheduledAt},
                #{progressCurrent}, #{progressTotal}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertJob(OpsAsyncJob job);

    @Select("""
            WITH candidate AS (
                SELECT id
                FROM ops_async_jobs
                WHERE attempt_count < max_attempts
                  AND cancel_requested_at IS NULL
                  AND (
                    (status IN ('QUEUED', 'RETRY_WAIT') AND scheduled_at <= CURRENT_TIMESTAMP)
                    OR (status = 'RUNNING' AND lease_until < CURRENT_TIMESTAMP)
                  )
                ORDER BY scheduled_at, created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE ops_async_jobs job
            SET status = 'RUNNING',
                attempt_count = job.attempt_count + 1,
                lease_owner = #{workerId},
                lease_until = CURRENT_TIMESTAMP + INTERVAL '2 minutes',
                heartbeat_at = CURRENT_TIMESTAMP,
                started_at = COALESCE(job.started_at, CURRENT_TIMESTAMP),
                finished_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            FROM candidate
            WHERE job.id = candidate.id
            RETURNING """ + " " + QUALIFIED_JOB_COLUMNS)
    OpsAsyncJob claimNext(String workerId);

    @Update("""
            UPDATE ops_async_jobs
            SET lease_until = CURRENT_TIMESTAMP + INTERVAL '2 minutes',
                heartbeat_at = CURRENT_TIMESTAMP,
                progress_current = #{progressCurrent}, progress_total = #{progressTotal},
                progress_message = #{progressMessage}, updated_at = CURRENT_TIMESTAMP
            WHERE job_id = #{jobId} AND status = 'RUNNING' AND lease_owner = #{workerId}
              AND cancel_requested_at IS NULL
            """)
    int heartbeat(@Param("jobId") Long jobId, @Param("workerId") String workerId,
                  @Param("progressCurrent") Long progressCurrent, @Param("progressTotal") Long progressTotal,
                  @Param("progressMessage") String progressMessage);

    @Update("""
            UPDATE ops_async_jobs
            SET status = 'SUCCEEDED', result_json = #{resultJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler},
                lease_owner = NULL, lease_until = NULL, heartbeat_at = CURRENT_TIMESTAMP,
                progress_current = CASE WHEN progress_total > 0 THEN progress_total ELSE progress_current END,
                finished_at = CURRENT_TIMESTAMP, last_error_code = NULL, last_error_message = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE job_id = #{jobId} AND status = 'RUNNING' AND lease_owner = #{workerId}
            """)
    int completeJob(@Param("jobId") Long jobId, @Param("workerId") String workerId,
                    @Param("resultJson") String resultJson);

    @Update("""
            UPDATE ops_async_jobs
            SET status = CASE WHEN attempt_count >= max_attempts THEN 'FAILED' ELSE 'RETRY_WAIT' END,
                scheduled_at = CASE attempt_count
                    WHEN 1 THEN CURRENT_TIMESTAMP + INTERVAL '30 seconds'
                    WHEN 2 THEN CURRENT_TIMESTAMP + INTERVAL '2 minutes'
                    WHEN 3 THEN CURRENT_TIMESTAMP + INTERVAL '10 minutes'
                    ELSE CURRENT_TIMESTAMP + INTERVAL '30 minutes'
                END,
                lease_owner = NULL, lease_until = NULL, heartbeat_at = CURRENT_TIMESTAMP,
                last_error_code = #{errorCode}, last_error_message = #{errorMessage},
                finished_at = CASE WHEN attempt_count >= max_attempts THEN CURRENT_TIMESTAMP ELSE NULL END,
                updated_at = CURRENT_TIMESTAMP
            WHERE job_id = #{jobId} AND status = 'RUNNING' AND lease_owner = #{workerId}
            """)
    int failJob(@Param("jobId") Long jobId, @Param("workerId") String workerId,
                @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE ops_async_jobs
            SET cancel_requested_at = CURRENT_TIMESTAMP,
                status = CASE WHEN status IN ('QUEUED', 'RETRY_WAIT') THEN 'CANCELLED' ELSE status END,
                finished_at = CASE WHEN status IN ('QUEUED', 'RETRY_WAIT') THEN CURRENT_TIMESTAMP ELSE finished_at END,
                updated_at = CURRENT_TIMESTAMP
            WHERE job_id = #{jobId} AND status NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED')
            """)
    int requestCancel(Long jobId);

    @Update("""
            UPDATE ops_async_jobs
            SET status = 'CANCELLED', lease_owner = NULL, lease_until = NULL,
                finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE job_id = #{jobId} AND status = 'RUNNING' AND lease_owner = #{workerId}
              AND cancel_requested_at IS NOT NULL
            """)
    int cancelClaimedJob(@Param("jobId") Long jobId, @Param("workerId") String workerId);

    @Update("""
            UPDATE ops_async_jobs
            SET status = 'QUEUED', attempt_count = 0, scheduled_at = CURRENT_TIMESTAMP,
                lease_owner = NULL, lease_until = NULL, heartbeat_at = NULL,
                cancel_requested_at = NULL, result_json = NULL,
                last_error_code = NULL, last_error_message = NULL, finished_at = NULL,
                progress_current = 0, progress_message = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE job_id = #{jobId} AND status IN ('FAILED', 'CANCELLED')
            """)
    int resetForRetry(Long jobId);

    @Insert("""
            INSERT INTO ops_migrations(migration_id, migration_type, status, progress_pct, summary_json, error_msg, started_at, finished_at)
            VALUES (#{migrationId}, #{migrationType}, #{status}, #{progressPct}, #{summaryJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler}, #{errorMsg}, #{startedAt}, #{finishedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMigration(OpsMigrationTask task);

    @Select("""
            SELECT id, migration_id, migration_type, status, progress_pct,
                   CAST(summary_json AS TEXT) AS summary_json, error_msg,
                   started_at, finished_at, created_at, updated_at
            FROM ops_migrations WHERE migration_id = #{migrationId} LIMIT 1
            """)
    OpsMigrationTask findMigrationById(Long migrationId);

    @Update("""
            UPDATE ops_migrations
            SET status = #{status}, progress_pct = #{progressPct},
                summary_json = #{summaryJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler},
                error_msg = #{errorMsg},
                finished_at = CASE WHEN #{status} IN ('done', 'failed') THEN CURRENT_TIMESTAMP(3) ELSE finished_at END,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE migration_id = #{migrationId}
            """)
    int updateMigration(@Param("migrationId") Long migrationId, @Param("status") String status,
                        @Param("progressPct") Integer progressPct, @Param("summaryJson") String summaryJson,
                        @Param("errorMsg") String errorMsg);
}
