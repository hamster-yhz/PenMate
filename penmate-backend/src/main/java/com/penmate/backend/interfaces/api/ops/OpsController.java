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
 * OpsController。
 * <p>控制层：负责HTTP请求接入、参数校验与统一响应封装。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class OpsController {

    private final OpsApplicationService opsApplicationService;

    public OpsController(OpsApplicationService opsApplicationService) {
        this.opsApplicationService = opsApplicationService;
    }

    /**
     * 查询详情数据。
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
     * 查询列表数据。
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
     * 处理业务请求。
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
     * 处理业务请求。
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
     * 查询详情数据。
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

