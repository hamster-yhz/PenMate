package com.penmate.backend.interfaces.api.approval.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data

public class CreateApprovalRequestDto {

    @NotNull
    private Long projectId;

    private Long taskId;

    @NotBlank
    private String approvalType;

    @NotBlank
    private String payloadJson;

    @NotNull
    private Integer riskLevel;

    @NotNull
    private Long requestedBy;

}

