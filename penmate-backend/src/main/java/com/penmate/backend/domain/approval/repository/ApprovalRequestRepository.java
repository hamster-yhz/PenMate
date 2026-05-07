package com.penmate.backend.domain.approval.repository;

import com.penmate.backend.domain.approval.model.ApprovalRequest;

import java.util.List;

public interface ApprovalRequestRepository {

    int insert(ApprovalRequest approvalRequest);

    List<ApprovalRequest> findByProjectId(Long projectId);

    ApprovalRequest findByApprovalRequestId(Long approvalRequestId);

    int approveByApprovalRequestId(Long approvalRequestId, Long reviewedBy, String comment);

    int rejectByApprovalRequestId(Long approvalRequestId, Long reviewedBy, String comment);
}

