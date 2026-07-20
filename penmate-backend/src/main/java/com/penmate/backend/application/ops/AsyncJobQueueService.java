package com.penmate.backend.application.ops;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.repository.OpsRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AsyncJobQueueService {
    private final OpsRepository repository;
    private final BusinessIdGenerator ids;

    public AsyncJobQueueService(OpsRepository repository, BusinessIdGenerator ids) {
        this.repository = repository;
        this.ids = ids;
    }

    @Transactional
    public OpsAsyncJob enqueue(String jobType, String bizKey, Long ownerUserId, Long projectId, String payloadJson) {
        OpsAsyncJob existing = repository.findJobByBizKey(bizKey);
        if (existing != null) return existing;
        OpsAsyncJob job = new OpsAsyncJob();
        job.setJobId(ids.nextId());
        job.setJobType(jobType);
        job.setBizKey(bizKey);
        job.setOwnerUserId(ownerUserId);
        job.setProjectId(projectId);
        job.setPayloadJson(payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson);
        job.setStatus("QUEUED");
        job.setAttemptCount(0);
        job.setMaxAttempts(5);
        job.setScheduledAt(Instant.now());
        job.setProgressCurrent(0L);
        job.setProgressTotal(0L);
        if (repository.insertJob(job) != 1) throw BusinessException.of("Failed to enqueue async job");
        return repository.findJobById(job.getJobId());
    }

    public void requestCancel(Long jobId) {
        if (repository.requestCancel(jobId) != 1) throw BusinessException.conflict("Async job cannot be cancelled");
    }

    public OpsAsyncJob retry(Long jobId) {
        if (repository.resetForRetry(jobId) != 1) throw BusinessException.conflict("Async job cannot be retried");
        return repository.findJobById(jobId);
    }
}
