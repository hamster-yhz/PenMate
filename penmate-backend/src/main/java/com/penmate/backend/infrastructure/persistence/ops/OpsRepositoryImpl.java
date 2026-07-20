package com.penmate.backend.infrastructure.persistence.ops;

import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.model.OpsMigrationTask;
import com.penmate.backend.domain.ops.repository.OpsRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 运维任务仓储实现。
 * <p>负责将异步任务与迁移任务的查询、创建、状态更新委托给 {@link OpsMapper}。</p>
 */
@Repository
public class OpsRepositoryImpl implements OpsRepository {

    private final OpsMapper opsMapper;

    public OpsRepositoryImpl(OpsMapper opsMapper) {
        this.opsMapper = opsMapper;
    }

    /**
     * 按任务 ID 查询异步作业详情。
     *
     * @param jobId 异步作业 ID
     * @return 异步作业；不存在时返回 {@code null}
     */
    @Override
    public OpsAsyncJob findJobById(Long jobId) {
        return opsMapper.findJobById(jobId);
    }

    @Override
    public OpsAsyncJob findJobByBizKey(String bizKey) { return opsMapper.findJobByBizKey(bizKey); }

    /**
     * 按业务键与任务类型筛选异步作业列表。
     *
     * @param bizKey 业务键（如项目、实体或批次标识）
     * @param jobType 作业类型
     * @return 命中的异步作业集合
     */
    @Override
    public List<OpsAsyncJob> listJobs(String bizKey, String jobType) {
        return opsMapper.listJobs(bizKey, jobType);
    }

    /**
     * 新增异步作业记录。
     *
     * @param job 待持久化的异步作业
     * @return 受影响行数
     */
    @Override
    public int insertJob(OpsAsyncJob job) {
        return opsMapper.insertJob(job);
    }

    /**
     * 更新异步作业状态与错误信息。
     *
     * @param jobId 作业 ID
     * @param status 作业状态
     * @param errorMsg 失败时的错误消息
     * @return 受影响行数
     */
    @Override
    public OpsAsyncJob claimNext(String workerId) { return opsMapper.claimNext(workerId); }

    @Override
    public int heartbeat(Long jobId, String workerId, Long progressCurrent, Long progressTotal, String progressMessage) {
        return opsMapper.heartbeat(jobId, workerId, progressCurrent, progressTotal, progressMessage);
    }

    @Override public int completeJob(Long jobId, String workerId, String resultJson) {
        return opsMapper.completeJob(jobId, workerId, resultJson);
    }
    @Override public int failJob(Long jobId, String workerId, String errorCode, String errorMessage) {
        return opsMapper.failJob(jobId, workerId, errorCode, errorMessage);
    }
    @Override public int requestCancel(Long jobId) { return opsMapper.requestCancel(jobId); }
    @Override public int cancelClaimedJob(Long jobId, String workerId) { return opsMapper.cancelClaimedJob(jobId, workerId); }
    @Override public int resetForRetry(Long jobId) { return opsMapper.resetForRetry(jobId); }

    /**
     * 新增内容迁移任务记录。
     *
     * @param task 待持久化的迁移任务
     * @return 受影响行数
     */
    @Override
    public int insertMigration(OpsMigrationTask task) {
        return opsMapper.insertMigration(task);
    }

    /**
     * 按迁移任务 ID 查询迁移执行状态。
     *
     * @param migrationId 迁移任务 ID
     * @return 迁移任务；不存在时返回 {@code null}
     */
    @Override
    public OpsMigrationTask findMigrationById(Long migrationId) {
        return opsMapper.findMigrationById(migrationId);
    }

    /**
     * 更新迁移任务进度、汇总信息与错误信息。
     *
     * @param migrationId 迁移任务 ID
     * @param status 当前迁移状态
     * @param progressPct 当前进度百分比
     * @param summaryJson 迁移汇总 JSON
     * @param errorMsg 失败时的错误消息
     * @return 受影响行数
     */
    @Override
    public int updateMigration(Long migrationId, String status, Integer progressPct, String summaryJson, String errorMsg) {
        return opsMapper.updateMigration(migrationId, status, progressPct, summaryJson, errorMsg);
    }
}

