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

@RestController
@RequestMapping("/api/v1")
public class OpsController {

    private final OpsApplicationService opsApplicationService;

    public OpsController(OpsApplicationService opsApplicationService) {
        this.opsApplicationService = opsApplicationService;
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<OpsAsyncJob> getJob(@PathVariable Long jobId,
                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(opsApplicationService.getJob(jobId), traceId);
    }

    @GetMapping("/jobs")
    public ApiResponse<List<OpsAsyncJob>> listJobs(@RequestParam(value = "bizKey", required = false) String bizKey,
                                                   @RequestParam(value = "jobType", required = false) String jobType,
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(opsApplicationService.listJobs(bizKey, jobType), traceId);
    }

    @PostMapping("/jobs/{jobId}/retry")
    public ApiResponse<OpsAsyncJob> retryJob(@PathVariable Long jobId,
                                             @RequestParam("operatorId") Long operatorId,
                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(opsApplicationService.retryJob(jobId, operatorId, traceId), traceId);
    }

    @PostMapping("/migrations/content-to-object-storage")
    public ApiResponse<OpsMigrationTask> runContentMigration(@RequestParam("operatorId") Long operatorId,
                                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(opsApplicationService.startContentToObjectStorageMigration(operatorId, traceId), traceId);
    }

    @GetMapping("/migrations/{migrationId}")
    public ApiResponse<OpsMigrationTask> getMigration(@PathVariable Long migrationId,
                                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(opsApplicationService.getMigration(migrationId), traceId);
    }
}

