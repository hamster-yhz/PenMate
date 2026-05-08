package com.penmate.backend.interfaces.api.approval.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data

public class CreateApprovalRequestDto {

    @NotBlank
    private String projectId;

    private String taskId;

    @NotBlank
    private String approvalType;

    @NotBlank
    private String payloadJson;

    @NotNull
    private Integer riskLevel;

    @NotBlank
    private String requestedBy;

}

