package com.penmate.backend.interfaces.api.ops;

import com.penmate.backend.application.ops.OpsApplicationService;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.model.OpsMigrationTask;
import com.penmate.backend.interfaces.api.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OpsControllerTest {

    @Mock
    private OpsApplicationService opsApplicationService;

    @InjectMocks
    private OpsController opsController;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(opsController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    // 查询任务详情成功。
    void UT_OPS_JOB_GET_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-OPS-JOB-GET";
        OpsAsyncJob job = new OpsAsyncJob();
        job.setId(6001L);
        job.setStatus("success");
        when(opsApplicationService.getJob(6001L)).thenReturn(job);

        mockMvc().perform(get("/api/v1/jobs/6001").header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(6001))
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 查询任务列表成功。
    void UT_OPS_JOB_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-OPS-JOB-LIST";
        OpsAsyncJob job = new OpsAsyncJob();
        job.setId(6002L);
        job.setJobType("migration");
        when(opsApplicationService.listJobs(isNull(), isNull())).thenReturn(List.of(job));

        mockMvc().perform(get("/api/v1/jobs").header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(6002))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 重试不可重试任务。
    void UT_OPS_JOB_RETRY_NOT_RETRYABLE() throws Exception {
        String traceId = "UT-TRACE-OPS-JOB-RETRY-NOT-RETRYABLE";
        doThrow(new IllegalArgumentException("Job is not retryable"))
                .when(opsApplicationService).retryJob(6003L, 1001L, traceId);

        mockMvc().perform(post("/api/v1/jobs/6003/retry")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 启动迁移成功。
    void UT_OPS_MIGRATION_RUN_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-OPS-MIGRATION-RUN";
        OpsMigrationTask task = new OpsMigrationTask();
        task.setId(7001L);
        task.setStatus("running");
        when(opsApplicationService.startContentToObjectStorageMigration(1001L, traceId)).thenReturn(task);

        mockMvc().perform(post("/api/v1/migrations/content-to-object-storage")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7001))
                .andExpect(jsonPath("$.data.status").value("running"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 启动迁移冲突。
    void UT_OPS_MIGRATION_RUN_CONFLICT() throws Exception {
        String traceId = "UT-TRACE-OPS-MIGRATION-CONFLICT";
        doThrow(new IllegalArgumentException("Migration already running"))
                .when(opsApplicationService).startContentToObjectStorageMigration(1001L, traceId);

        mockMvc().perform(post("/api/v1/migrations/content-to-object-storage")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    // 重试任务成功。
    void UT_OPS_JOB_RETRY_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-OPS-JOB-RETRY-SUCCESS";
        OpsAsyncJob job = new OpsAsyncJob();
        job.setId(6003L);
        job.setStatus("queued");
        when(opsApplicationService.retryJob(6003L, 1001L, traceId)).thenReturn(job);

        mockMvc().perform(post("/api/v1/jobs/6003/retry")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(6003))
                .andExpect(jsonPath("$.data.status").value("queued"));
    }

    @Test
    // 查询迁移任务成功。
    void UT_OPS_MIGRATION_GET_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-OPS-MIGRATION-GET";
        OpsMigrationTask task = new OpsMigrationTask();
        task.setId(7001L);
        task.setStatus("success");
        when(opsApplicationService.getMigration(7001L)).thenReturn(task);

        mockMvc().perform(get("/api/v1/migrations/7001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7001))
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }
}

