package com.penmate.backend.infrastructure.persistence.approval;

import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.approval.repository.ApprovalRequestRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ApprovalRequestRepositoryImpl。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Repository
public class ApprovalRequestRepositoryImpl implements ApprovalRequestRepository {

    private final ApprovalRequestMapper approvalRequestMapper;

    public ApprovalRequestRepositoryImpl(ApprovalRequestMapper approvalRequestMapper) {
        this.approvalRequestMapper = approvalRequestMapper;
    }

    /**
     * 处理业务请求。
     *
     * @param approvalRequest 入参：approvalRequest
     * @return 出参：处理结果
     */
    @Override
    public int insert(ApprovalRequest approvalRequest) {
        return approvalRequestMapper.insert(approvalRequest);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    @Override
    public List<ApprovalRequest> findByProjectId(Long projectId) {
        return approvalRequestMapper.findByProjectId(projectId);
    }

    /**
     * 处理业务请求。
     *
     * @param id 入参：id
     * @return 出参：处理结果
     */
    @Override
    public ApprovalRequest findById(Long id) {
        return approvalRequestMapper.findById(id);
    }

    /**
     * 处理业务请求。
     *
     * @param id 入参：id
     * @param reviewedBy 入参：reviewedBy
     * @param comment 入参：comment
     * @return 出参：处理结果
     */
    @Override
    public int approve(Long id, Long reviewedBy, String comment) {
        return approvalRequestMapper.approve(id, reviewedBy, comment);
    }

    /**
     * 处理业务请求。
     *
     * @param id 入参：id
     * @param reviewedBy 入参：reviewedBy
     * @param comment 入参：comment
     * @return 出参：处理结果
     */
    @Override
    public int reject(Long id, Long reviewedBy, String comment) {
        return approvalRequestMapper.reject(id, reviewedBy, comment);
    }
}

