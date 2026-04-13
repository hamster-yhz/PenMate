package com.penmate.backend.domain.approval.repository;

import com.penmate.backend.domain.approval.model.ApprovalRequest;

import java.util.List;

public interface ApprovalRequestRepository {

    int insert(ApprovalRequest approvalRequest);

    List<ApprovalRequest> findByProjectId(Long projectId);

    ApprovalRequest findById(Long id);

    int approve(Long id, Long reviewedBy, String comment);

    int reject(Long id, Long reviewedBy, String comment);
}

