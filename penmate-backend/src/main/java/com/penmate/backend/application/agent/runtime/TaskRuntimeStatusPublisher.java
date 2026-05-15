package com.penmate.backend.application.agent.runtime;

/**
 * Task runtime status publisher 应用层端口。
 */
public interface TaskRuntimeStatusPublisher {

    void publishStarted(Long projectId, RuntimeStatusView runtimeStatusView);

    void publishStatus(Long projectId, RuntimeStatusView runtimeStatusView);

    void publishToolCall(Long projectId, RuntimeStatusView runtimeStatusView);

    void publishWaitingApproval(Long projectId, RuntimeStatusView runtimeStatusView);

    void publishDone(Long projectId, RuntimeStatusView runtimeStatusView);

    void publishFailed(Long projectId, RuntimeStatusView runtimeStatusView);
}
