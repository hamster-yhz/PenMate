package com.penmate.backend.interfaces.api.approval.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class ReviewApprovalRequestDto {

    @NotBlank
    private String reviewedBy;

    private String comment;

}

