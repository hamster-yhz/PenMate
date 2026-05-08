package com.penmate.backend.domain.shared.model;

/**
 * 领域共享的审批展示视图。
 * <p>该对象仅承载实时事件广播所需的稳定审批展示字段，
 * 避免 [`RealtimeEventService.java`](penmate-backend/src/main/java/com/penmate/backend/domain/shared/service/RealtimeEventService.java) 依赖应用层定义。</p>
 */
public record ApprovalView(
        String toolCode,
        String toolDisplayName,
        Integer riskLevel,
        String approvalType,
        String operationCode
) {
}
