package com.penmate.backend.application.agent.orchestration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步触发 generation workflow，避免阻塞创建任务接口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentGenerationWorkflowDispatcher {

    private final AgentGenerationWorkflow agentGenerationWorkflow;

    @Async
    public void dispatchInitialRun(Long projectId, Long taskId, String traceId) {
        execute(projectId, taskId, traceId, false);
    }

    @Async
    public void dispatchResumeAfterApproval(Long projectId, Long taskId, String traceId) {
        execute(projectId, taskId, traceId, true);
    }

    private void execute(Long projectId, Long taskId, String traceId, boolean resumeAfterApproval) {
        try {
            if (resumeAfterApproval) {
                agentGenerationWorkflow.runAfterApproval(projectId, taskId, traceId);
            } else {
                agentGenerationWorkflow.run(projectId, taskId, traceId);
            }
        } catch (Exception ex) {
            log.error("异步编排执行异常: projectId={}, taskId={}, traceId={}", projectId, taskId, traceId, ex);
        }
    }
}
