package com.penmate.backend.infrastructure.persistence.approval;

import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.approval.repository.ApprovalRequestRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ApprovalRequestRepositoryImpl implements ApprovalRequestRepository {

    private final ApprovalRequestMapper approvalRequestMapper;

    public ApprovalRequestRepositoryImpl(ApprovalRequestMapper approvalRequestMapper) {
        this.approvalRequestMapper = approvalRequestMapper;
    }

    @Override
    public int insert(ApprovalRequest approvalRequest) {
        return approvalRequestMapper.insert(approvalRequest);
    }

    @Override
    public List<ApprovalRequest> findByProjectId(Long projectId) {
        return approvalRequestMapper.findByProjectId(projectId);
    }

    @Override
    public ApprovalRequest findById(Long id) {
        return approvalRequestMapper.findById(id);
    }

    @Override
    public int approve(Long id, Long reviewedBy, String comment) {
        return approvalRequestMapper.approve(id, reviewedBy, comment);
    }

    @Override
    public int reject(Long id, Long reviewedBy, String comment) {
        return approvalRequestMapper.reject(id, reviewedBy, comment);
    }
}

