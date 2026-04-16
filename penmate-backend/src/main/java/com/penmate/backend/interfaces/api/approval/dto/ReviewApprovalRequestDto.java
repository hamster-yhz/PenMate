package com.penmate.backend.interfaces.api.approval.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data

public class ReviewApprovalRequestDto {

    @NotNull
    private Long reviewedBy;

    private String comment;

}

