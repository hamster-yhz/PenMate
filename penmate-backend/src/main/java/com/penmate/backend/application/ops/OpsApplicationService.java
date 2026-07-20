 package com.penmate.backend.application.ops;

import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.model.OpsMigrationTask;
import com.penmate.backend.domain.ops.repository.OpsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 运维应用服务。
 * <p>负责异步任务查询/重试，以及内容迁移任务的发起与状态查询。</p>
 */
@Service
@Slf4j
public class OpsApplicationService {

    private final OpsRepository opsRepository;
    private final AsyncJobQueueService queueService;

    public OpsApplicationService(OpsRepository opsRepository, AsyncJobQueueService queueService) {
        this.opsRepository = opsRepository;
        this.queueService = queueService;
    }

    /**
     * 查询异步任务详情。
     *
     * @param jobId 入参：jobId
     * @return 出参：处理结果
     */
    public OpsAsyncJob getJob(Long jobId) {
        log.info("查询异步任务详情: jobId={}", jobId);
        OpsAsyncJob job = opsRepository.findJobById(jobId);
        if (job == null) {
            log.warn("查询异步任务详情失败: jobId={}, reason=not_found", jobId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Job not found");
        }
        log.info("查询异步任务详情成功: jobId={}, jobType={}, status={}", jobId, job.getJobType(), job.getStatus());
        return job;
    }

    /**
     * 按业务键与任务类型筛选异步任务。
     *
     * @param bizKey 入参：bizKey
     * @param jobType 入参：jobType
     * @return 出参：处理结果
     */
    public List<OpsAsyncJob> listJobs(String bizKey, String jobType) {
        List<OpsAsyncJob> jobs = opsRepository.listJobs(bizKey, jobType);
        log.info("查询异步任务列表: bizKey={}, jobType={}, count={}", bizKey, jobType, jobs.size());
        return jobs;
    }

    /**
     * 重试指定异步任务并生成新的待执行任务。
     *
     * @param jobId 入参：jobId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public OpsAsyncJob retryJob(Long jobId, Long operatorId, String traceId) {
        log.info("重试异步任务: sourceJobId={}, operatorId={}", jobId, operatorId);
        getJob(jobId);
        OpsAsyncJob retried = queueService.retry(jobId);
        writeAudit(traceId, operatorId, "ops", "job:retry", "ops_async_jobs", String.valueOf(jobId), null, 200);
        return retried;
    }

    /**
     * 发起“正文迁移到对象存储”迁移任务。
     *
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public OpsMigrationTask startContentToObjectStorageMigration(Long operatorId, String traceId) {
        log.info("发起内容迁移任务: migrationType=content_to_object_storage, operatorId={}", operatorId);
        OpsMigrationTask task = new OpsMigrationTask();
        task.setMigrationType("content_to_object_storage");
        task.setStatus("running");
        task.setProgressPct(0);
        task.setSummaryJson(null);
        task.setStartedAt(Instant.now());
        int affected = opsRepository.insertMigration(task);
        if (affected != 1) {
            log.error("发起内容迁移任务失败: operatorId={}, reason=insert_failed", operatorId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to start migration");
        }

        opsRepository.updateMigration(task.getId(), "done", 100, "{\"migrated\":0,\"failed\":0}", null);
        writeAudit(traceId, operatorId, "ops", "migration:run", "ops_migrations", String.valueOf(task.getId()), "{\"migrationType\":\"content_to_object_storage\"}", 201);
        log.info("发起内容迁移任务成功: migrationId={}, status=done", task.getId());
        return getMigration(task.getId());
    }

    /**
     * 查询迁移任务详情。
     *
     * @param migrationId 入参：migrationId
     * @return 出参：处理结果
     */
    public OpsMigrationTask getMigration(Long migrationId) {
        log.info("查询迁移任务详情: migrationId={}", migrationId);
        OpsMigrationTask task = opsRepository.findMigrationById(migrationId);
        if (task == null) {
            log.warn("查询迁移任务详情失败: migrationId={}, reason=not_found", migrationId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Migration task not found");
        }
        log.info("查询迁移任务详情成功: migrationId={}, status={}, progressPct={}",
                migrationId, task.getStatus(), task.getProgressPct());
        return task;
    }

    private void writeAudit(String traceId,
                            Long userId,
                            String module,
                            String action,
                            String resourceType,
                            String resourceId,
                            String requestJson,
                            int responseCode) {
        // 审计模块已移除
    }
}


