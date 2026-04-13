package com.penmate.backend.domain.ops.repository;

import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.model.OpsMigrationTask;

import java.util.List;

public interface OpsRepository {

    OpsAsyncJob findJobById(Long jobId);

    List<OpsAsyncJob> listJobs(String bizKey, String jobType);

    int insertJob(OpsAsyncJob job);

    int updateJobStatus(Long jobId, String status, String errorMsg);

    int insertMigration(OpsMigrationTask task);

    OpsMigrationTask findMigrationById(Long migrationId);

    int updateMigration(Long migrationId, String status, Integer progressPct, String summaryJson, String errorMsg);
}

