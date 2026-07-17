package com.penmate.backend.domain.approval.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * 审批请求实体。
 */
public class ApprovalRequest {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 审批请求业务 ID。 */
    private Long approvalRequestId;
    /** 关联项目业务 ID。 */
    private Long projectId;
    /** 关联任务业务 ID。 */
    private Long runId;
    /** 审批类型（发布审批、高风险操作审批等）。 */
    private String approvalType;
    /** 审批业务载荷（JSON）。 */
    private String payloadJson;
    /** 风险等级。 */
    private Integer riskLevel;
    /** 审批状态。 */
    private String status;
    /** 发起人用户业务 ID。 */
    private Long requestedBy;
    /** 审核人用户业务 ID。 */
    private Long reviewedBy;
    /** 审核完成时间。 */
    private LocalDateTime reviewedAt;
    /** 审核意见。 */
    private String reviewComment;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;

}

