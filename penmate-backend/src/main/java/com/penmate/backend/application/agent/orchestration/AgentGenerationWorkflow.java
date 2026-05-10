package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 生成主工作流。
 * <p>该工作流负责把一次生成任务的完整链路串起来：任务状态推进、模型执行配置解析、prompt 装配、tool loop、结果发布与失败封口。</p>
 * <p>它是跨应用服务、领域仓储、外部网关的长流程协调者，因此更偏 orchestration，而不是单一 CRUD 应用服务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentGenerationWorkflow {

    private static final int ERROR_MSG_MAX_LENGTH = 500;

    private final AgentRepository agentRepository;
    private final com.penmate.backend.application.agent.AgentTaskStateMachine taskStateMachine;
    private final RealtimeEventService realtimeEventService;
    private final AgentToolLoopRunner agentToolLoopRunner;
    private final AgentModelRoutingService agentModelRoutingService;
    private final AgentPromptAssembler agentPromptAssembler;
    private final AgentResultPublisher agentResultPublisher;
    private final AgentTaskRuntimeUpdater agentTaskRuntimeUpdater;
    private final AgentTaskResultRecorder agentTaskResultRecorder;
    private final SessionStyleBindingAppService sessionStyleBindingAppService;

    public void run(Long projectId, Long taskId, String traceId) {
        runInternal(projectId, taskId, traceId);
    }

    public void runAfterApproval(Long projectId, Long taskId, String traceId) {
        runInternal(projectId, taskId, traceId);
    }

    private void runInternal(Long projectId, Long taskId, String traceId) {
        AgentGenerationTask task = agentRepository.findGenerationTask(projectId, taskId);
        if (task == null) {
            log.warn("编排任务不存在: projectId={}, taskId={}, traceId={}", projectId, taskId, traceId);
            return;
        }
        if (task.getTaskId() == null) {
            log.error("编排任务缺少 taskId，终止执行: projectId={}, requestedTaskId={}, physicalId={}, traceId={}",
                    projectId,
                    taskId,
                    task.getId(),
                    traceId);
            return;
        }
 
        try {
            transitionStatus(projectId, task, AgentTaskStatus.RUNNING, null);
            realtimeEventService.publishGenerationStarted(projectId, taskId);
            realtimeEventService.publishGenerationStatus(projectId, taskId, "planning", "正在分析请求并规划工具调用", AgentTaskStatus.RUNNING.value());

            AgentTaskContext taskContext = buildTaskContext(projectId, task);
            AgentLlmExecutionConfig executionConfig = agentModelRoutingService.resolveExecutionConfig(task.getUserId(), task.getModelConfigId(), traceId);
            long llmStartAt = System.currentTimeMillis();
            AgentToolLoopIterationResult loopResult = agentToolLoopRunner.execute(
                    projectId,
                    taskId,
                    task.getConversationId(),
                    0L,
                    traceId,
                    agentPromptAssembler.buildInitialMessages(task, taskContext, List.of()),
                    executionConfig
            );
            if (loopResult.waitingApproval()) {
                transitionStatus(projectId, task, AgentTaskStatus.WAITING_APPROVAL, null);
                return;
            }
            String generatedText = loopResult.finalAssistantText();
            long llmCostMs = System.currentTimeMillis() - llmStartAt;
            log.info("agent.llm.generate.finished: projectId={}, taskId={}, traceId={}, costMs={}, outputLength={}",
                    projectId,
                    taskId,
                    traceId,
                    llmCostMs,
                    safeLength(generatedText));

            agentTaskRuntimeUpdater.updateGenerationRuntime(projectId, taskId, task.getPromptSnapshot(), generatedText, traceId);
            agentResultPublisher.publishGenerationTokens(projectId, taskId, generatedText, traceId);

            agentTaskResultRecorder.recordAssistantResult(task, generatedText);
            transitionStatus(projectId, task, AgentTaskStatus.DONE, null);
            realtimeEventService.publishGenerationDone(projectId, taskId, AgentTaskStatus.DONE.value());
        } catch (Exception ex) {
            log.error("编排执行失败: projectId={}, taskId={}, traceId={}", projectId, taskId, traceId, ex);
            transitionToFailed(projectId, task, ex);
            realtimeEventService.publishGenerationFailed(projectId, taskId, "AGENT_MODEL_CALL_FAILED", ex.getMessage());
        }
    }

    private AgentTaskContext buildTaskContext(Long projectId, AgentGenerationTask task) {
        AgentTaskContext taskContext = AgentTaskContext.recoveryOf(task.getTaskId(), task.getStatus(), null);
        if (sessionStyleBindingAppService != null) {
            taskContext.setStyleSnapshotJson(sessionStyleBindingAppService.getBoundStyleSnapshotJson(projectId, task.getConversationId()));
        }
        return taskContext;
    }

    private void transitionToFailed(Long projectId, AgentGenerationTask task, Exception ex) {
        try {
            transitionStatus(projectId, task, AgentTaskStatus.FAILED, safeErrorMessage(ex));
        } catch (Exception transitionEx) {
            log.error("失败状态回写异常: projectId={}, taskId={}", projectId, task.getTaskId(), transitionEx);
        }
    }

    private void transitionStatus(Long projectId, AgentGenerationTask task, AgentTaskStatus targetStatus, String errorMsg) {
        taskStateMachine.assertTransition(task.getStatus(), targetStatus);
        int affected = agentRepository.updateGenerationTaskStatus(projectId, task.getTaskId(), targetStatus.value(), errorMsg);
        if (affected != 1) {
            throw new IllegalStateException("Failed to update generation task status");
        }
        task.setStatus(targetStatus.value());
    }

    private int safeLength(String text) {
        return text == null ? 0 : text.length();
    }

    private String safeErrorMessage(Exception ex) {
        if (ex == null || ex.getMessage() == null) {
            return null;
        }
        String message = ex.getMessage().trim();
        if (message.length() <= ERROR_MSG_MAX_LENGTH) {
            return message;
        }
        String truncated = message.substring(0, ERROR_MSG_MAX_LENGTH - 3) + "...";
        log.warn("任务失败错误信息已截断: originalLength={}, truncatedLength={}, exceptionType={}",
                message.length(),
                truncated.length(),
                ex.getClass().getName());
        return truncated;
    }
}
