package com.penmate.backend.application.ops;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.repository.OpsRepository;

public class AsyncJobExecutionContext {
    private final OpsRepository repository;
    private final Long jobId;
    private final String workerId;

    AsyncJobExecutionContext(OpsRepository repository, Long jobId, String workerId) {
        this.repository = repository;
        this.jobId = jobId;
        this.workerId = workerId;
    }

    public void heartbeat(long current, long total, String message) {
        OpsAsyncJob latest = repository.findJobById(jobId);
        if (latest == null || latest.cancellationRequested()) throw new JobCancelledException();
        if (repository.heartbeat(jobId, workerId, current, total, message) != 1) {
            throw BusinessException.conflict("Async job lease was lost");
        }
    }

    public boolean cancellationRequested() {
        OpsAsyncJob latest = repository.findJobById(jobId);
        return latest == null || latest.cancellationRequested();
    }

    public static final class JobCancelledException extends RuntimeException {
        public JobCancelledException() { super("Async job cancellation requested"); }
    }
}
