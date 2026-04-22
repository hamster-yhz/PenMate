package com.penmate.backend.application.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步触发编排执行，避免阻塞创建任务接口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrationDispatcher {

    private final AgentOrchestrator agentOrchestrator;

    /**
     * 首次异步调度入口。
     * <p>由创建任务接口触发，不跳过审批门禁。</p>
     */
    @Async
    public void dispatch(Long projectId, Long taskId, String traceId) {
        execute(projectId, taskId, traceId, false);
    }

    /**
     * 审批通过后的异步恢复入口。
     * <p>恢复执行时跳过审批门禁，避免重复创建审批单。</p>
     */
    @Async
    public void dispatchAfterApproval(Long projectId, Long taskId, String traceId) {
        execute(projectId, taskId, traceId, true);
    }

    /**
     * 异步调度统一执行器。
     * <p>线程边界上做异常兜底，避免异常冒泡影响调用线程。</p>
     */
    private void execute(Long projectId, Long taskId, String traceId, boolean skipApprovalGate) {
        try {
            if (skipApprovalGate) {
                agentOrchestrator.runAfterApproval(projectId, taskId, traceId);
            } else {
                agentOrchestrator.run(projectId, taskId, traceId);
            }
        } catch (Exception ex) {
            log.error("异步编排执行异常: projectId={}, taskId={}, traceId={}", projectId, taskId, traceId, ex);
        }
    }
}
