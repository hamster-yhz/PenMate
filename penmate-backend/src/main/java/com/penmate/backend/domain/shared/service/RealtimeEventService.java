package com.penmate.backend.domain.shared.service;

import com.penmate.backend.domain.shared.model.ApprovalView;

public interface RealtimeEventService {

    void publishProjectEvent(Long projectId, String eventType, Object data);

    void publishGenerationStarted(Long projectId, Long taskId);

    void publishGenerationToken(Long projectId, Long taskId, String token, boolean done);

    default void publishGenerationToolCall(Long projectId,
                                           Long taskId,
                                           String pluginCode,
                                           String toolName,
                                           String status,
                                           String errorMsg,
                                           String output) {
        publishGenerationToolCall(projectId,
                taskId,
                null,
                pluginCode,
                toolName,
                status,
                null,
                null,
                null,
                null,
                errorMsg,
                output);
    }

    void publishGenerationToolCall(Long projectId,
                                   Long taskId,
                                   String toolCallId,
                                   String pluginCode,
                                   String toolName,
                                   String status,
                                   Long approvalId,
                                   String approvalType,
                                   Integer iteration,
                                   Object argumentsPreview,
                                   String errorMsg,
                                   String output);

    default void publishGenerationWaitingApproval(Long projectId, Long taskId, Long approvalId, String approvalType) {
        publishGenerationWaitingApproval(projectId, taskId, null, approvalId, approvalType, null, null, null);
    }

    void publishGenerationWaitingApproval(Long projectId,
                                          Long taskId,
                                          String toolCallId,
                                          Long approvalId,
                                          String approvalType,
                                          Object approvalPreview,
                                          String resumeMode,
                                          ApprovalView approvalView);

    void publishGenerationDone(Long projectId, Long taskId, String status);

    void publishGenerationFailed(Long projectId, Long taskId, String errorCode, String errorMsg);
}
