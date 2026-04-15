package com.penmate.backend.infrastructure.persistence.ops;

import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.model.OpsMigrationTask;
import com.penmate.backend.domain.ops.repository.OpsRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * OpsRepositoryImpl。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Repository
public class OpsRepositoryImpl implements OpsRepository {

    private final OpsMapper opsMapper;

    public OpsRepositoryImpl(OpsMapper opsMapper) {
        this.opsMapper = opsMapper;
    }

    /**
     * 处理业务请求。
     *
     * @param jobId 入参：jobId
     * @return 出参：处理结果
     */
    @Override
    public OpsAsyncJob findJobById(Long jobId) {
        return opsMapper.findJobById(jobId);
    }

    /**
     * 查询列表数据。
     *
     * @param bizKey 入参：bizKey
     * @param jobType 入参：jobType
     * @return 出参：处理结果
     */
    @Override
    public List<OpsAsyncJob> listJobs(String bizKey, String jobType) {
        return opsMapper.listJobs(bizKey, jobType);
    }

    /**
     * 处理业务请求。
     *
     * @param job 入参：job
     * @return 出参：处理结果
     */
    @Override
    public int insertJob(OpsAsyncJob job) {
        return opsMapper.insertJob(job);
    }

    /**
     * 更新业务数据。
     *
     * @param jobId 入参：jobId
     * @param status 入参：status
     * @param errorMsg 入参：errorMsg
     * @return 出参：处理结果
     */
    @Override
    public int updateJobStatus(Long jobId, String status, String errorMsg) {
        return opsMapper.updateJobStatus(jobId, status, errorMsg);
    }

    /**
     * 处理业务请求。
     *
     * @param task 入参：task
     * @return 出参：处理结果
     */
    @Override
    public int insertMigration(OpsMigrationTask task) {
        return opsMapper.insertMigration(task);
    }

    /**
     * 处理业务请求。
     *
     * @param migrationId 入参：migrationId
     * @return 出参：处理结果
     */
    @Override
    public OpsMigrationTask findMigrationById(Long migrationId) {
        return opsMapper.findMigrationById(migrationId);
    }

    /**
     * 更新业务数据。
     *
     * @param migrationId 入参：migrationId
     * @param status 入参：status
     * @param progressPct 入参：progressPct
     * @param summaryJson 入参：summaryJson
     * @param errorMsg 入参：errorMsg
     * @return 出参：处理结果
     */
    @Override
    public int updateMigration(Long migrationId, String status, Integer progressPct, String summaryJson, String errorMsg) {
        return opsMapper.updateMigration(migrationId, status, progressPct, summaryJson, errorMsg);
    }
}

