package com.penmate.backend.application.agent;

import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.rag.RagRetrievalService;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.approval.repository.ApprovalRequestRepository;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 编排入口：驱动状态机与SSE主通道。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrator {

    private static final int ERROR_MSG_MAX_LENGTH = 500;

    private final AgentRepository agentRepository;
    private final AgentTaskStateMachine taskStateMachine;
    private final RealtimeEventService realtimeEventService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final AgentLlmGateway agentLlmGateway;
    private final RagRetrievalService ragRetrievalService;
    private final PluginToolCoordinator pluginToolCoordinator;
    private final AgentModelRoutingService agentModelRoutingService;

    /**
     * 标准编排入口。
     * <p>用于新建任务后的首次执行，会走审批门禁判断。</p>
     */
    public void run(Long projectId, Long taskId, String traceId) {
        runInternal(projectId, taskId, traceId, false);
    }

    /**
     * 审批通过后的恢复入口。
     * <p>跳过审批门禁，直接继续 RAG/工具/模型生成链路。</p>
     */
    public void runAfterApproval(Long projectId, Long taskId, String traceId) {
        runInternal(projectId, taskId, traceId, true);
    }

    /**
     * Agent 编排主流程。
     * <p>状态迁移 -> 审批门禁(可选) -> RAG -> Tool -> LLM -> 持久化消息 -> 完成事件。</p>
     */
    private void runInternal(Long projectId, Long taskId, String traceId, boolean skipApprovalGate) {
        AgentGenerationTask task = agentRepository.findGenerationTask(projectId, taskId);
        if (task == null) {
            log.warn("编排任务不存在: projectId={}, taskId={}, traceId={}", projectId, taskId, traceId);
            return;
        }

        try {
            // 先进入 running，再向前端广播 started，保证前端拉详情时状态已一致。
            transitionStatus(projectId, task, AgentTaskStatus.RUNNING, null);
            realtimeEventService.publishGenerationStarted(projectId, taskId);

            // 首次执行且命中高风险规则时，先挂起为 waiting_approval，由审批流程恢复执行。
            if (!skipApprovalGate && shouldPauseForApproval(task)) {
                ApprovalRequest approvalRequest = createApprovalRequest(task);
                transitionStatus(projectId, task, AgentTaskStatus.WAITING_APPROVAL, null);
                realtimeEventService.publishGenerationWaitingApproval(projectId, taskId, approvalRequest.getId(), approvalRequest.getApprovalType());
                return;
            }

            // 检索增强：把知识库片段作为后续模型生成上下文的一部分。
            List<RagRetrievedChunk> ragChunks = ragRetrievalService.retrieve(
                    projectId,
                    taskId,
                    task.getPromptSnapshot(),
                    traceId
            ).chunks();

            // 工具调用失败不终止主链路，仅清空工具上下文继续生成，避免整体不可用。
            ToolExecutionResult toolResult = pluginToolCoordinator.execute(new ToolExecutionRequest(
                    projectId,
                    taskId,
                    task.getPromptSnapshot(),
                    traceId
            ));
            String toolContext = toolResult.success() ? toolResult.output() : "";

            // 模型路由：完全显式模式，严格使用任务携带的模型配置ID，不做默认策略兜底。
            AgentLlmExecutionConfig executionConfig = agentModelRoutingService.resolveExecutionConfig(projectId, task.getModelConfigId(), traceId);
            long llmStartAt = System.currentTimeMillis();
            String generatedText = agentLlmGateway.generate(task, ragChunks, toolContext, executionConfig);
            long llmCostMs = System.currentTimeMillis() - llmStartAt;
            log.info("agent.llm.generate.finished: projectId={}, taskId={}, traceId={}, costMs={}, outputLength={}",
                    projectId,
                    taskId,
                    traceId,
                    llmCostMs,
                    safeLength(generatedText));
            String tokenUsageJson = "{\"inputTokens\":" + safeLength(task.getPromptSnapshot()) + ",\"outputTokens\":" + safeLength(generatedText) + "}";
            String costJson = "{\"currency\":\"USD\",\"estimated\":" + String.format(java.util.Locale.ROOT, "%.6f", estimateCost(generatedText)) + "}";
            agentRepository.updateGenerationTaskRuntime(projectId, taskId, tokenUsageJson, costJson, traceId);

            // 先分片推送 token，再持久化完整 assistant 消息，兼容流式消费与历史追溯。
            List<String> chunks = splitToChunks(generatedText);
            log.info("agent.sse.token.publish.start: projectId={}, taskId={}, traceId={}, chunkCount={}",
                    projectId,
                    taskId,
                    traceId,
                    chunks.size());
            int tokenIndex = 0;
            for (String token : chunks) {
                tokenIndex += 1;
                if (tokenIndex == 1) {
                    log.info("agent.sse.token.publish.first: projectId={}, taskId={}, traceId={}, firstChunkLength={}",
                            projectId,
                            taskId,
                            traceId,
                            safeLength(token));
                }
                realtimeEventService.publishGenerationToken(projectId, taskId, token, false);
            }
            log.info("agent.sse.token.publish.end: projectId={}, taskId={}, traceId={}, publishedChunkCount={}",
                    projectId,
                    taskId,
                    traceId,
                    tokenIndex);

            persistAssistantMessage(task, generatedText);
            transitionStatus(projectId, task, AgentTaskStatus.DONE, null);
            realtimeEventService.publishGenerationDone(projectId, taskId, AgentTaskStatus.DONE.value());
        } catch (Exception ex) {
            log.error("编排执行失败: projectId={}, taskId={}, traceId={}", projectId, taskId, traceId, ex);
            transitionToFailed(projectId, task, ex);
            realtimeEventService.publishGenerationFailed(projectId, taskId, "AGENT_MODEL_CALL_FAILED", ex.getMessage());
        }
    }

    private boolean shouldPauseForApproval(AgentGenerationTask task) {
        String taskType = task.getTaskType() == null ? "" : task.getTaskType().toUpperCase();
        if (taskType.contains("WORLD") || taskType.contains("SETTING")) {
            return true;
        }
        String prompt = task.getPromptSnapshot() == null ? "" : task.getPromptSnapshot();
        return prompt.contains("世界设定") || prompt.contains("新增设定");
    }

    private ApprovalRequest createApprovalRequest(AgentGenerationTask task) {
        AgentConversation conversation = agentRepository.findConversation(task.getProjectId(), task.getConversationId());
        Long requestedBy = conversation == null || conversation.getUserId() == null ? 0L : conversation.getUserId();
        ApprovalRequest request = new ApprovalRequest();
        request.setProjectId(task.getProjectId());
        request.setTaskId(task.getId());
        request.setApprovalType("WORLD_SETTING_CREATE");
        request.setPayloadJson(task.getPromptSnapshot() == null ? "{}" : task.getPromptSnapshot());
        request.setRiskLevel(2);
        request.setRequestedBy(requestedBy);
        request.setStatus("pending");

        int affected = approvalRequestRepository.insert(request);
        if (affected != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create approval request");
        }
        realtimeEventService.publishProjectEvent(task.getProjectId(), "approval.created", java.util.Map.of(
                "approvalId", request.getId(),
                "taskId", request.getTaskId(),
                "approvalType", request.getApprovalType(),
                "riskLevel", request.getRiskLevel(),
                "status", request.getStatus()
        ));
        return request;
    }

    /**
     * 失败兜底：尽量把任务状态回写为 failed，避免任务长时间停留在 running。
     */
    private void transitionToFailed(Long projectId, AgentGenerationTask task, Exception ex) {
        try {
            transitionStatus(projectId, task, AgentTaskStatus.FAILED, safeErrorMessage(ex));
        } catch (Exception transitionEx) {
            log.error("失败状态回写异常: projectId={}, taskId={}", projectId, task.getId(), transitionEx);
        }
    }

    /**
     * 统一状态迁移入口。
     * <p>先做状态机守卫，再做数据库回写，最后刷新内存对象状态。</p>
     */
    private void transitionStatus(Long projectId, AgentGenerationTask task, AgentTaskStatus targetStatus, String errorMsg) {
        taskStateMachine.assertTransition(task.getStatus(), targetStatus);
        int affected = agentRepository.updateGenerationTaskStatus(projectId, task.getId(), targetStatus.value(), errorMsg);
        if (affected != 1) {
            throw new IllegalStateException("Failed to update generation task status");
        }
        task.setStatus(targetStatus.value());
    }

    /**
     * 持久化生成结果为 assistant 消息，并刷新会话最近消息时间。
     */
    private void persistAssistantMessage(AgentGenerationTask task, String generatedText) {
        AgentMessage assistantMessage = new AgentMessage();
        assistantMessage.setConversationId(task.getConversationId());
        assistantMessage.setRole("assistant");
        assistantMessage.setUserMessageType("GENERATION_RESULT");
        assistantMessage.setContentMd(generatedText);
        assistantMessage.setAttachmentsJson("[]");
        assistantMessage.setToolCallsJson("[]");
        assistantMessage.setSeqNo(agentRepository.nextMessageSeq(task.getConversationId()));
        agentRepository.insertMessage(assistantMessage);
        agentRepository.touchConversationLastMessage(task.getConversationId());
    }


    /**
     * 把完整文本拆为固定窗口 token 片段，用于模拟/支持流式分发。
     */
    private List<String> splitToChunks(String text) {
        List<String> chunks = new ArrayList<>();
        int step = 12;
        for (int i = 0; i < text.length(); i += step) {
            chunks.add(text.substring(i, Math.min(i + step, text.length())));
        }
        if (chunks.isEmpty()) {
            chunks.add("");
        }
        return chunks;
    }

    private int safeLength(String text) {
        return text == null ? 0 : text.length();
    }

    private double estimateCost(String generatedText) {
        return safeLength(generatedText) * 0.000002D;
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

