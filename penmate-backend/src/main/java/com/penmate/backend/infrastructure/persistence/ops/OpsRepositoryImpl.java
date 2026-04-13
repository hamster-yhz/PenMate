package com.penmate.backend.infrastructure.persistence.ops;

import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.model.OpsMigrationTask;
import com.penmate.backend.domain.ops.repository.OpsRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class OpsRepositoryImpl implements OpsRepository {

    private final OpsMapper opsMapper;

    public OpsRepositoryImpl(OpsMapper opsMapper) {
        this.opsMapper = opsMapper;
    }

    @Override
    public OpsAsyncJob findJobById(Long jobId) {
        return opsMapper.findJobById(jobId);
    }

    @Override
    public List<OpsAsyncJob> listJobs(String bizKey, String jobType) {
        return opsMapper.listJobs(bizKey, jobType);
    }

    @Override
    public int insertJob(OpsAsyncJob job) {
        return opsMapper.insertJob(job);
    }

    @Override
    public int updateJobStatus(Long jobId, String status, String errorMsg) {
        return opsMapper.updateJobStatus(jobId, status, errorMsg);
    }

    @Override
    public int insertMigration(OpsMigrationTask task) {
        return opsMapper.insertMigration(task);
    }

    @Override
    public OpsMigrationTask findMigrationById(Long migrationId) {
        return opsMapper.findMigrationById(migrationId);
    }

    @Override
    public int updateMigration(Long migrationId, String status, Integer progressPct, String summaryJson, String errorMsg) {
        return opsMapper.updateMigration(migrationId, status, progressPct, summaryJson, errorMsg);
    }
}

