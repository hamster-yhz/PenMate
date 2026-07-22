package com.penmate.backend.domain.ops.model;

import lombok.Data;

import java.time.Instant;

@Data
public class OpsAsyncJob {
    private Long id;
    private Long jobId;
    private String jobType;
    private String bizKey;
    private Long ownerUserId;
    private Long projectId;
    private String payloadJson;
    private String resultJson;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private Instant scheduledAt;
    private String leaseOwner;
    private Instant leaseUntil;
    private Instant heartbeatAt;
    private Instant cancelRequestedAt;
    private Long progressCurrent;
    private Long progressTotal;
    private String progressMessage;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public boolean terminal() {
        return "SUCCEEDED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
    }

    public boolean cancellationRequested() {
        return cancelRequestedAt != null;
    }
}
