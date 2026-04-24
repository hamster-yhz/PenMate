package com.penmate.backend.interfaces.api.ops;

import com.penmate.backend.application.ops.OpsApplicationService;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.model.OpsMigrationTask;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运维任务控制器。
 * <p>负责异步任务查询/重试、内容迁移任务触发与迁移进度查询等运维接口的 HTTP 接入。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class OpsController {

    private final OpsApplicationService opsApplicationService;

    public OpsController(OpsApplicationService opsApplicationService) {
        this.opsApplicationService = opsApplicationService;
    }

    /**
     * 查询异步作业详情。
     * <p><b>业务目的：</b>返回指定作业的执行状态、错误信息与重试信息，供运维面板展示。</p>
     * <p><b>流程主线：</b>接收作业业务 ID -> 调用应用服务查询 -> 统一封装响应。</p>
     * <p><b>关键调用：</b>{@code opsApplicationService.getJob(jobId)}。</p>
     * <p><b>ID 语义：</b>jobId 为异步作业业务 ID。</p>
     * <p><b>异常与分支：</b>作业不存在时返回业务异常。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     *
     * @param jobId 入参：jobId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/jobs/{jobId}")
    public ApiResponse<OpsAsyncJob> getJob(@PathVariable Long jobId,
                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(opsApplicationService.getJob(jobId), traceId);
    }

    /**
     * 查询异步作业列表。
     * <p><b>业务目的：</b>按业务键与作业类型筛选作业，支持运维排障与批量巡检。</p>
     * <p><b>流程主线：</b>读取可选过滤参数 -> 调用应用服务查询列表 -> 返回结果集合。</p>
     * <p><b>关键调用：</b>{@code opsApplicationService.listJobs(bizKey, jobType)}。</p>
     * <p><b>异常与分支：</b>过滤参数为空时返回默认范围作业列表。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     *
     * @param bizKey 入参：bizKey
     * @param jobType 入参：jobType
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/jobs")
    public ApiResponse<List<OpsAsyncJob>> listJobs(@RequestParam(value = "bizKey", required = false) String bizKey,
                                                   @RequestParam(value = "jobType", required = false) String jobType,
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(opsApplicationService.listJobs(bizKey, jobType), traceId);
    }

    /**
     * 重试失败作业。
     * <p><b>业务目的：</b>对可重试作业发起重新执行，缩短人工修复链路。</p>
     * <p><b>流程主线：</b>接收作业与操作者参数 -> 调用应用服务执行重试 -> 返回更新后的作业状态。</p>
     * <p><b>关键调用：</b>{@code opsApplicationService.retryJob(jobId, operatorId, traceId)}。</p>
     * <p><b>ID 语义：</b>jobId、operatorId 均为业务语义 ID。</p>
     * <p><b>异常与分支：</b>作业状态不允许重试或操作者无权限时返回业务异常。</p>
     * <p><b>副作用：</b>更新作业执行状态，可能触发新的异步执行。</p>
     *
     * @param jobId 入参：jobId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/jobs/{jobId}/retry")
    public ApiResponse<OpsAsyncJob> retryJob(@PathVariable Long jobId,
                                             @RequestParam("operatorId") Long operatorId,
                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(opsApplicationService.retryJob(jobId, operatorId, traceId), traceId);
    }

    /**
     * 启动“内容迁移到对象存储”任务。
     * <p><b>业务目的：</b>发起历史内容从原存储形态到对象存储的迁移流程。</p>
     * <p><b>流程主线：</b>接收操作者参数 -> 调用应用服务创建迁移任务 -> 返回迁移任务快照。</p>
     * <p><b>关键调用：</b>{@code opsApplicationService.startContentToObjectStorageMigration(operatorId, traceId)}。</p>
     * <p><b>ID 语义：</b>operatorId 为操作者业务 ID。</p>
     * <p><b>异常与分支：</b>已有同类迁移在执行或权限不足时返回业务异常。</p>
     * <p><b>副作用：</b>创建迁移任务并触发后台迁移执行。</p>
     *
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/migrations/content-to-object-storage")
    public ApiResponse<OpsMigrationTask> runContentMigration(@RequestParam("operatorId") Long operatorId,
                                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(opsApplicationService.startContentToObjectStorageMigration(operatorId, traceId), traceId);
    }

    /**
     * 查询迁移任务详情。
     * <p><b>业务目的：</b>返回迁移任务进度、统计与失败信息，支持迁移过程监控。</p>
     * <p><b>流程主线：</b>接收迁移任务业务 ID -> 调用应用服务查询 -> 封装响应。</p>
     * <p><b>关键调用：</b>{@code opsApplicationService.getMigration(migrationId)}。</p>
     * <p><b>ID 语义：</b>migrationId 为迁移任务业务 ID。</p>
     * <p><b>异常与分支：</b>迁移任务不存在时返回业务异常。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     *
     * @param migrationId 入参：migrationId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/migrations/{migrationId}")
    public ApiResponse<OpsMigrationTask> getMigration(@PathVariable Long migrationId,
                                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(opsApplicationService.getMigration(migrationId), traceId);
    }
}

