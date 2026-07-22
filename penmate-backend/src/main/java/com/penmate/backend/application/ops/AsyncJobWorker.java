package com.penmate.backend.application.ops;

import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.repository.OpsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AsyncJobWorker {
    private final OpsRepository repository;
    private final Map<String, AsyncJobHandler> handlers;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName();

    public AsyncJobWorker(OpsRepository repository, List<AsyncJobHandler> handlers) {
        this.repository = repository;
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(AsyncJobHandler::jobType, Function.identity()));
    }

    public void poll() {
        OpsAsyncJob job = repository.claimNext(workerId);
        if (job == null) return;
        AsyncJobExecutionContext context = new AsyncJobExecutionContext(repository, job.getJobId(), workerId);
        AsyncJobHandler handler = handlers.get(job.getJobType());
        if (handler == null) {
            repository.failJob(job.getJobId(), workerId, "JOB_HANDLER_MISSING", "No handler for " + job.getJobType());
            return;
        }
        try {
            String result = handler.execute(job, context);
            if (context.cancellationRequested()) repository.cancelClaimedJob(job.getJobId(), workerId);
            else repository.completeJob(job.getJobId(), workerId, result == null ? "{}" : result);
        } catch (AsyncJobExecutionContext.JobCancelledException cancelled) {
            repository.cancelClaimedJob(job.getJobId(), workerId);
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            log.warn("Async job failed: jobId={}, type={}, attempt={}", job.getJobId(), job.getJobType(), job.getAttemptCount(), exception);
            repository.failJob(job.getJobId(), workerId, "JOB_EXECUTION_FAILED", truncate(message, 1000));
        }
    }

    private String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
