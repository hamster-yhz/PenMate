package com.penmate.backend.interfaces.api.approval.dto;

import jakarta.validation.constraints.NotNull;

public class ReviewApprovalRequestDto {

    @NotNull
    private Long reviewedBy;

    private String comment;

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}

