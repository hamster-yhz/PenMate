package com.penmate.backend.domain.ops.repository;

import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.model.OpsMigrationTask;

import java.util.List;

public interface OpsRepository {

    OpsAsyncJob findJobById(Long jobId);

    OpsAsyncJob findJobByBizKey(String bizKey);

    List<OpsAsyncJob> listJobs(String bizKey, String jobType);

    OpsAsyncJob findLatestProjectJob(Long projectId, String jobType);

    int insertJob(OpsAsyncJob job);

    OpsAsyncJob claimNext(String workerId);

    int heartbeat(Long jobId, String workerId, Long progressCurrent, Long progressTotal, String progressMessage);

    int completeJob(Long jobId, String workerId, String resultJson);

    int failJob(Long jobId, String workerId, String errorCode, String errorMessage);

    int requestCancel(Long jobId);

    int cancelClaimedJob(Long jobId, String workerId);

    int resetForRetry(Long jobId);

    int insertMigration(OpsMigrationTask task);

    OpsMigrationTask findMigrationById(Long migrationId);

    int updateMigration(Long migrationId, String status, Integer progressPct, String summaryJson, String errorMsg);
}

