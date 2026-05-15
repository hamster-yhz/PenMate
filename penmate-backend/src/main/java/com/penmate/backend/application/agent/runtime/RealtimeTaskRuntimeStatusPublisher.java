package com.penmate.backend.application.agent.runtime;

import com.penmate.backend.domain.shared.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基于 realtime service 的 task runtime status publisher。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeTaskRuntimeStatusPublisher implements TaskRuntimeStatusPublisher {

    private final RealtimeEventService realtimeEventService;

    @Override
    public void publishStarted(Long projectId, RuntimeStatusView runtimeStatusView) {
        publish(projectId, "generation.started", runtimeStatusView);
    }

    @Override
    public void publishStatus(Long projectId, RuntimeStatusView runtimeStatusView) {
        publish(projectId, "generation.status", runtimeStatusView);
    }

    @Override
    public void publishToolCall(Long projectId, RuntimeStatusView runtimeStatusView) {
        publish(projectId, "generation.tool_call", runtimeStatusView);
    }

    @Override
    public void publishWaitingApproval(Long projectId, RuntimeStatusView runtimeStatusView) {
        publish(projectId, "generation.waiting_approval", runtimeStatusView);
    }

    @Override
    public void publishDone(Long projectId, RuntimeStatusView runtimeStatusView) {
        publish(projectId, "generation.done", runtimeStatusView);
    }

    @Override
    public void publishFailed(Long projectId, RuntimeStatusView runtimeStatusView) {
        publish(projectId, "generation.failed", runtimeStatusView);
    }

    private void publish(Long projectId, String eventType, RuntimeStatusView runtimeStatusView) {
        if (runtimeStatusView == null) {
            return;
        }
        log.info("task runtime status publish: eventType={}, phase={}, taskId={}, sessionId={}, toolName={}, recoverable={}",
                eventType,
                runtimeStatusView.phase(),
                runtimeStatusView.taskId(),
                runtimeStatusView.sessionId(),
                runtimeStatusView.toolCall() == null ? null : runtimeStatusView.toolCall().toolName(),
                runtimeStatusView.recoverable());
        realtimeEventService.publishTaskRuntimeStatus(projectId, eventType, runtimeStatusView);
    }
}
