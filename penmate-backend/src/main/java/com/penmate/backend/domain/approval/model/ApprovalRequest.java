package com.penmate.backend.domain.approval.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class ApprovalRequest {
    private Long id;
    private Long projectId;
    private Long taskId;
    private String approvalType;
    private String payloadJson;
    private Integer riskLevel;
    private String status;
    private Long requestedBy;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}

