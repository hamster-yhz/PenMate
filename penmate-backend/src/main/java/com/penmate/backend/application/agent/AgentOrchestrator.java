package com.penmate.backend.application.agent;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.loop.AgentToolLoopController;
import com.penmate.backend.application.agent.loop.AgentToolLoopIterationResult;
import com.penmate.backend.application.rag.RagRetrievalService;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final AgentToolLoopController agentToolLoopController;
    private final RagRetrievalService ragRetrievalService;
    private final AgentModelRoutingService agentModelRoutingService;

    /**
     * 标准编排入口。
     * <p>用于新建任务后的首次执行。</p>
     */
    public void run(Long projectId, Long taskId, String traceId) {
        runInternal(projectId, taskId, traceId);
    }

    /**
     * 审批通过后的恢复入口。
     * <p>当前保留为继续编排入口。</p>
     */
    public void runAfterApproval(Long projectId, Long taskId, String traceId) {
        runInternal(projectId, taskId, traceId);
    }

    /**
     * Agent 编排主流程。
     * <p>状态迁移 -> RAG -> Tool -> LLM -> 持久化消息 -> 完成事件。</p>
     */
    private void runInternal(Long projectId, Long taskId, String traceId) {
        AgentGenerationTask task = agentRepository.findGenerationTask(projectId, taskId);
        if (task == null) {
            log.warn("编排任务不存在: projectId={}, taskId={}, traceId={}", projectId, taskId, traceId);
            return;
        }

        try {
            // 先进入 running，再向前端广播 started，保证前端拉详情时状态已一致。
            transitionStatus(projectId, task, AgentTaskStatus.RUNNING, null);
            realtimeEventService.publishGenerationStarted(projectId, taskId);

            // 检索增强：把知识库片段作为后续模型生成上下文的一部分。
            List<RagRetrievedChunk> ragChunks = ragRetrievalService.retrieve(
                    projectId,
                    taskId,
                    task.getPromptSnapshot(),
                    traceId
            ).chunks();

            List<Map<String, Object>> initialMessages = buildInitialMessages(task, ragChunks);

            // 模型路由：完全显式模式，严格使用任务携带的模型配置ID，不做默认策略兜底。
            AgentLlmExecutionConfig executionConfig = agentModelRoutingService.resolveExecutionConfig(projectId, task.getModelConfigId(), traceId);
            long llmStartAt = System.currentTimeMillis();
            //委派给agentToolLoopController执行，进入工具调用循环，直到模型生成完成或进入等待审批
            AgentToolLoopIterationResult loopResult = agentToolLoopController.execute(
                    projectId,
                    taskId,
                    task.getConversationId(),
                    0L,
                    traceId,
                    initialMessages,
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

    private List<Map<String, Object>> buildInitialMessages(AgentGenerationTask task, List<RagRetrievedChunk> ragChunks) {
        String prompt = task.getPromptSnapshot() == null ? "" : task.getPromptSnapshot().trim();
        String style = task.getStyleProfileSnapshot() == null ? "" : task.getStyleProfileSnapshot().trim();
        StringBuilder builder = new StringBuilder();

        if (!style.isEmpty()) {
            builder.append("写作风格约束：\n").append(style).append("\n\n");
        }
        if (ragChunks != null && !ragChunks.isEmpty()) {
            builder.append("知识库参考：\n");
            for (RagRetrievedChunk chunk : ragChunks) {
                builder.append("- [")
                        .append(chunk.getDocumentTitle() == null ? "文档" : chunk.getDocumentTitle())
                        .append("#")
                        .append(chunk.getChunkNo() == null ? 0 : chunk.getChunkNo())
                        .append("] ")
                        .append(chunk.getContentText() == null ? "" : chunk.getContentText())
                        .append("\n");
            }
            builder.append("\n");
        }
        builder.append("用户指令：\n").append(prompt);
        return List.of(Map.of(
                "role", "user",
                "content", builder.toString()
        ));
    }
}

