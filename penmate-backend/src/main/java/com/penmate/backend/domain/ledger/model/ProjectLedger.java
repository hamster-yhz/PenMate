package com.penmate.backend.domain.ledger.model;

import lombok.Data;

import java.time.Instant;

@Data
public class ProjectLedger {
    private Long ledgerId;
    private Long projectId;
    private String title;
    private String content;
    private Long contentRevision;
    private String leaseOwnerType;
    private Long leaseOwnerId;
    private String leaseToken;
    private Instant leaseExpiresAt;
    private Instant createdAt;
    private Instant updatedAt;
}
