package com.penmate.backend.application.ops;

import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.model.OpsMigrationTask;
import com.penmate.backend.domain.ops.repository.OpsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpsApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private OpsRepository opsRepository;

    @InjectMocks
    private OpsApplicationService opsApplicationService;

    @Test
    void UT_APP_OPS_GET_JOB_NOT_FOUND() {
        when(opsRepository.findJobById(9L)).thenReturn(null);

        assertThatThrownBy(() -> opsApplicationService.getJob(9L))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Job not found");
    }

    @Test
    void UT_APP_OPS_LIST_JOBS_SUCCESS() {
        when(opsRepository.listJobs("biz-1", "import")).thenReturn(List.of(new OpsAsyncJob(), new OpsAsyncJob()));

        List<OpsAsyncJob> result = opsApplicationService.listJobs("biz-1", "import");

        assertThat(result).hasSize(2);
        verify(opsRepository).listJobs("biz-1", "import");
        verifyNoInteractions(auditService);
    }

    @Test
    void UT_APP_OPS_RETRY_JOB_SUCCESS() {
        Long sourceJobId = 11L;
        Long operatorId = 1001L;
        String traceId = "UT-TRACE-OPS-RETRY";

        OpsAsyncJob oldJob = new OpsAsyncJob();
        oldJob.setId(sourceJobId);
        oldJob.setJobType("migration");
        oldJob.setBizKey("novel:1");

        OpsAsyncJob createdJob = new OpsAsyncJob();
        createdJob.setId(12L);
        createdJob.setStatus("pending");

        when(opsRepository.findJobById(sourceJobId)).thenReturn(oldJob);
        when(opsRepository.insertJob(any(OpsAsyncJob.class))).thenAnswer(invocation -> {
            OpsAsyncJob arg = invocation.getArgument(0);
            arg.setId(12L);
            return 1;
        });
        when(opsRepository.findJobById(12L)).thenReturn(createdJob);

        OpsAsyncJob result = opsApplicationService.retryJob(sourceJobId, operatorId, traceId);

        assertThat(result.getId()).isEqualTo(12L);
        verify(opsRepository).insertJob(any(OpsAsyncJob.class));
        verify(auditService).write(eq(traceId), eq(operatorId), eq("ops"), eq("job:retry"), eq("ops_async_jobs"), eq("12"), eq("{\"sourceJobId\":11}"), eq(201));
    }

    @Test
    void UT_APP_OPS_START_MIGRATION_SUCCESS() {
        Long operatorId = 1001L;
        String traceId = "UT-TRACE-OPS-MIGRATION";

        OpsMigrationTask doneTask = new OpsMigrationTask();
        doneTask.setId(31L);
        doneTask.setStatus("done");
        doneTask.setProgressPct(100);

        when(opsRepository.insertMigration(any(OpsMigrationTask.class))).thenAnswer(invocation -> {
            OpsMigrationTask arg = invocation.getArgument(0);
            arg.setId(31L);
            return 1;
        });
        when(opsRepository.updateMigration(31L, "done", 100, "{\"migrated\":0,\"failed\":0}", null)).thenReturn(1);
        when(opsRepository.findMigrationById(31L)).thenReturn(doneTask);

        OpsMigrationTask result = opsApplicationService.startContentToObjectStorageMigration(operatorId, traceId);

        assertThat(result.getId()).isEqualTo(31L);
        verify(opsRepository).updateMigration(31L, "done", 100, "{\"migrated\":0,\"failed\":0}", null);
        verify(auditService).write(eq(traceId), eq(operatorId), eq("ops"), eq("migration:run"), eq("ops_migrations"), eq("31"), eq("{\"migrationType\":\"content_to_object_storage\"}"), eq(201));
    }

    @Test
    void UT_APP_OPS_GET_MIGRATION_NOT_FOUND() {
        when(opsRepository.findMigrationById(99L)).thenReturn(null);

        assertThatThrownBy(() -> opsApplicationService.getMigration(99L))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Migration task not found");
    }
}

