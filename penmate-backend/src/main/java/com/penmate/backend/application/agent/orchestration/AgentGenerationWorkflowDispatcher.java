package com.penmate.backend.application.agent.orchestration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Agent 生成工作流异步分发器。
 * <p>负责把“首次执行”与“审批后恢复执行”两类长流程切到异步线程中运行，避免接口层或审批回调线程直接承载完整编排耗时。</p>
 * <p>该类只负责触发与兜底日志，不承载生成流程本身的业务决策。</p>
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
