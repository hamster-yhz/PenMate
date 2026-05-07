package com.penmate.backend.infrastructure.persistence.approval;

import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.approval.repository.ApprovalRequestRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 审批请求仓储实现。
 * <p>负责将审批单的创建、查询与审核状态变更委托给 {@link ApprovalRequestMapper}。</p>
 */
@Repository
public class ApprovalRequestRepositoryImpl implements ApprovalRequestRepository {

    private final ApprovalRequestMapper approvalRequestMapper;

    public ApprovalRequestRepositoryImpl(ApprovalRequestMapper approvalRequestMapper) {
        this.approvalRequestMapper = approvalRequestMapper;
    }

    /**
     * 新增审批请求。
     *
     * @param approvalRequest 审批请求聚合
     * @return 受影响行数
     */
    @Override
    public int insert(ApprovalRequest approvalRequest) {
        return approvalRequestMapper.insert(approvalRequest);
    }

    /**
     * 按项目查询审批请求列表。
     *
     * @param projectId 项目 ID
     * @return 审批请求集合
     */
    @Override
    public List<ApprovalRequest> findByProjectId(Long projectId) {
        return approvalRequestMapper.findByProjectId(projectId);
    }

    /**
     * 按审批业务 ID 查询详情。
     *
     * @param approvalRequestId 审批业务 ID
     * @return 审批请求；不存在时返回 {@code null}
     */
    @Override
    public ApprovalRequest findByApprovalRequestId(Long approvalRequestId) {
        return approvalRequestMapper.findByApprovalRequestId(approvalRequestId);
    }

    /**
     * 将审批请求标记为通过。
     *
     * @param approvalRequestId 审批业务 ID
     * @param reviewedBy 审核人 ID
     * @param comment 审核意见
     * @return 受影响行数
     */
    @Override
    public int approveByApprovalRequestId(Long approvalRequestId, Long reviewedBy, String comment) {
        return approvalRequestMapper.approveByApprovalRequestId(approvalRequestId, reviewedBy, comment);
    }

    /**
     * 将审批请求标记为拒绝。
     *
     * @param approvalRequestId 审批业务 ID
     * @param reviewedBy 审核人 ID
     * @param comment 审核意见
     * @return 受影响行数
     */
    @Override
    public int rejectByApprovalRequestId(Long approvalRequestId, Long reviewedBy, String comment) {
        return approvalRequestMapper.rejectByApprovalRequestId(approvalRequestId, reviewedBy, comment);
    }
}

