package com.penmate.backend.application.ops;

import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.model.OpsMigrationTask;
import com.penmate.backend.domain.ops.repository.OpsRepository;
import com.penmate.backend.domain.shared.service.AuditService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * OpsApplicationService。
 * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
 */
@Service
public class OpsApplicationService {

    private final OpsRepository opsRepository;
    private final AuditService auditService;

    public OpsApplicationService(OpsRepository opsRepository,
                                 AuditService auditService) {
        this.opsRepository = opsRepository;
        this.auditService = auditService;
    }

    /**
     * 查询详情数据。
     *
     * @param jobId 入参：jobId
     * @return 出参：处理结果
     */
    public OpsAsyncJob getJob(Long jobId) {
        OpsAsyncJob job = opsRepository.findJobById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Job not found");
        }
        return job;
    }

    /**
     * 查询列表数据。
     *
     * @param bizKey 入参：bizKey
     * @param jobType 入参：jobType
     * @return 出参：处理结果
     */
    public List<OpsAsyncJob> listJobs(String bizKey, String jobType) {
        return opsRepository.listJobs(bizKey, jobType);
    }

    /**
     * 处理业务请求。
     *
     * @param jobId 入参：jobId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public OpsAsyncJob retryJob(Long jobId, Long operatorId, String traceId) {
        OpsAsyncJob oldJob = getJob(jobId);
        OpsAsyncJob newJob = new OpsAsyncJob();
        newJob.setJobType(oldJob.getJobType());
        newJob.setBizKey(oldJob.getBizKey());
        newJob.setStatus("pending");
        newJob.setErrorMsg(null);
        int affected = opsRepository.insertJob(newJob);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to create retry job");
        }
        writeAudit(traceId, operatorId, "ops", "job:retry", "ops_async_jobs", String.valueOf(newJob.getId()), "{\"sourceJobId\":" + jobId + "}", 201);
        return getJob(newJob.getId());
    }

    /**
     * 处理业务请求。
     *
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public OpsMigrationTask startContentToObjectStorageMigration(Long operatorId, String traceId) {
        OpsMigrationTask task = new OpsMigrationTask();
        task.setMigrationType("content_to_object_storage");
        task.setStatus("running");
        task.setProgressPct(0);
        task.setSummaryJson(null);
        task.setStartedAt(LocalDateTime.now());
        int affected = opsRepository.insertMigration(task);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to start migration");
        }

        opsRepository.updateMigration(task.getId(), "done", 100, "{\"migrated\":0,\"failed\":0}", null);
        writeAudit(traceId, operatorId, "ops", "migration:run", "ops_migrations", String.valueOf(task.getId()), "{\"migrationType\":\"content_to_object_storage\"}", 201);
        return getMigration(task.getId());
    }

    /**
     * 查询详情数据。
     *
     * @param migrationId 入参：migrationId
     * @return 出参：处理结果
     */
    public OpsMigrationTask getMigration(Long migrationId) {
        OpsMigrationTask task = opsRepository.findMigrationById(migrationId);
        if (task == null) {
            throw new IllegalArgumentException("Migration task not found");
        }
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
        String finalTraceId = (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
        auditService.write(finalTraceId, userId, module, action, resourceType, resourceId, requestJson, responseCode);
    }
}

