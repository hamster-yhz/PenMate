package com.penmate.backend.domain.shared.service;

public interface RealtimeEventService {

    void publishProjectEvent(Long projectId, String eventType, Object data);

    void publishGenerationStarted(Long projectId, Long taskId);

    void publishGenerationToken(Long projectId, Long taskId, String token, boolean done);

    void publishGenerationToolCall(Long projectId,
                                   Long taskId,
                                   String pluginCode,
                                   String toolName,
                                   String status,
                                   String errorMsg,
                                   String output);

    void publishGenerationWaitingApproval(Long projectId, Long taskId, Long approvalId, String approvalType);

    void publishGenerationDone(Long projectId, Long taskId, String status);

    void publishGenerationFailed(Long projectId, Long taskId, String errorCode, String errorMsg);
}

