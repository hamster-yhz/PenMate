package com.penmate.backend.application.approval.command;

/**
 * ReviewApprovalCommand。
 * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
 */
public record ReviewApprovalCommand(
        Long reviewedBy,
        String comment
) {
}

