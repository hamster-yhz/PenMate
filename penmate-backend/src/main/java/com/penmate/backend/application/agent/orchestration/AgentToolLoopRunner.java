package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.llm.LlmTokenUsage;
import com.penmate.backend.application.agent.runtime.RuntimeStatusView;
import com.penmate.backend.application.agent.runtime.TaskRuntimeStatusPublisher;
import com.penmate.backend.application.agent.runtime.ToolCallStatusView;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.gateway.ToolCallApplicationService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResumeService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallSnapshotMapper;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent tool-calling 主循环执行器。
 * <p>该类负责把一次 LLM turn、模型返回的 tool calls、tool 执行结果回填以及多轮对话续跑串成闭环。</p>
 * <p>它只负责编排 loop 本身：限制轮次与单轮 tool 数量、维护消息上下文、把 tool result 回写为下一轮消息；
 * 具体 tool 业务执行与审批治理分别委托给
 * {@link com.penmate.backend.application.agent.tool.handler.AgentToolHandler} 与
 * {@link ToolCallApplicationService}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolLoopRunner {

    private static final int MAX_TOOL_TURNS = 4;
    private static final int MAX_TOOL_CALLS_PER_TURN = 3;

    private final AgentLlmGateway agentLlmGateway;
    private final ToolCallApplicationService toolCallApplicationService;
    private final AgentToolDefinitionSource toolDefinitionSource;
    private final ToolCallResumeService toolCallResumeService;
    private final ToolCallSnapshotMapper toolCallSnapshotMapper;
    private final AgentRepository agentRepository;
    private final TaskRuntimeStatusPublisher taskRuntimeStatusPublisher;

    public AgentToolLoopIterationResult execute(Long projectId,
                                                Long taskId,
                                                Long conversationId,
                                                Long operatorId,
                                                String traceId,
                                                List<AgentLlmMessage> initialMessages,
                                                AgentLlmExecutionConfig executionConfig) {
        List<AgentLlmMessage> messages = new ArrayList<>(initialMessages == null ? List.of() : initialMessages);
        List<AgentLlmToolSchema> tools = toolDefinitionSource.listLlmSchemas();
        StringBuilder toolContextBuilder = new StringBuilder();
        int totalToolCalls = 0;
        LlmTokenUsage totalTokenUsage = LlmTokenUsage.ZERO;
        AgentTaskContext taskContext = loadTaskContext(taskId);
        Long turnId = taskContext == null ? null : taskContext.getTurnId();

        for (int turnIndex = 0; turnIndex < MAX_TOOL_TURNS; turnIndex++) {
            AgentLlmTurnResponse response = agentLlmGateway.generateTurn(
                    new AgentLlmTurnRequest(messages, tools, "auto"),
                    executionConfig
            );
            totalTokenUsage = totalTokenUsage.add(response.tokenUsage());
            if ("tool_calls".equalsIgnoreCase(response.finishReason()) && response.toolCalls().isEmpty()) {
                throw new IllegalStateException("LLM finishReason=tool_calls but toolCalls is empty");
            }
            if (!response.requestsToolCalls()) {
                return AgentToolLoopIterationResult.completed(
                        response.assistantText(),
                        totalToolCalls,
                        toolContextBuilder.toString(),
                        totalTokenUsage
                );
            }
            ensureToolCallsPerTurnWithinLimit(response.toolCalls());

            messages.add(toolCallSnapshotMapper.buildAssistantToolCallMessage(response));
            String assistantToolCallsJson = toolCallSnapshotMapper.toAssistantToolCallsJson(response.toolCalls());
            String loopRunId = buildLoopRunId(taskId, traceId);
            for (AgentLlmToolCall toolCall : response.toolCalls()) {
                totalToolCalls += 1;
                ToolCallResult toolResult = toolCallApplicationService.executeToolCall(new ToolCallRequest(
                        projectId,
                        taskId,
                        conversationId,
                        toolCall.toolCode(),
                        toolCall.argumentsJson(),
                        operatorId,
                        traceId,
                        "{}",
                        buildIdempotencyKey(taskId, toolCall),
                        loopRunId,
                        turnIndex,
                        toolCall.id(),
                        assistantToolCallsJson,
                        toolCallSnapshotMapper.toConversationMessagesJson(messages),
                        "RESUME_LOOP",
                        null
                ));
                publishToolCallStatus(projectId, taskId, conversationId, taskContext, turnId, toolCall, turnIndex, toolResult);
                if ("WAITING_APPROVAL".equals(toolResult.status())) {
                    if (toolResult.approvalId() == null) {
                        throw new IllegalStateException("WAITING_APPROVAL result missing approvalId");
                    }
                    return AgentToolLoopIterationResult.waitingApproval(
                            toolResult.approvalId(),
                            totalToolCalls,
                            toolContextBuilder.toString(),
                            totalTokenUsage
                    );
                }

                String toolOutput = extractToolOutput(toolResult, toolCall);
                appendToolContext(toolContextBuilder, toolOutput);
                messages.add(AgentLlmMessage.tool(toolCall.id(), toolOutput));
            }
        }

        throw new IllegalStateException("Agent tool loop exceeded max turns: " + MAX_TOOL_TURNS);
    }

    public ToolCallResult resumeFromPending(ApprovalRequest request, PendingToolInvocationSnapshot snapshot) {
        return toolCallResumeService.resumeFromPending(request, snapshot);
    }

    private void ensureToolCallsPerTurnWithinLimit(List<AgentLlmToolCall> toolCalls) {
        int count = toolCalls == null ? 0 : toolCalls.size();
        if (count > MAX_TOOL_CALLS_PER_TURN) {
            throw new IllegalStateException("Agent tool loop exceeded max tool calls per turn: " + MAX_TOOL_CALLS_PER_TURN);
        }
    }


    private String extractToolOutput(ToolCallResult toolResult, AgentLlmToolCall toolCall) {
        if (toolResult == null) {
            throw new IllegalStateException("Tool invocation result is null");
        }
        if (!"SUCCESS".equals(toolResult.status())) {
            log.warn("agent.tool.loop.tool.failed: toolCode={}, status={}, errorCode={}, errorMessage= {}",
                    toolCall == null ? null : toolCall.toolCode(),
                    toolResult.status(), toolResult.errorCode(), toolResult.errorMessage());
            String errorCode = toolResult.errorCode() == null ? "TOOL_EXECUTION_FAILED" : toolResult.errorCode();
            String errorMessage = toolResult.errorMessage() == null ? "unknown tool execution failure" : toolResult.errorMessage();
            throw new IllegalStateException(errorCode + ": " + errorMessage);
        }
        return toolResult.toolOutput() == null ? "" : toolResult.toolOutput();
    }

    private void appendToolContext(StringBuilder toolContextBuilder, String toolOutput) {
        if (toolOutput == null || toolOutput.isBlank()) {
            return;
        }
        if (!toolContextBuilder.isEmpty()) {
            toolContextBuilder.append("\n");
        }
        toolContextBuilder.append(toolOutput);
    }

    private String buildIdempotencyKey(Long taskId, AgentLlmToolCall toolCall) {
        String callId = toolCall == null || toolCall.id() == null || toolCall.id().isBlank()
                ? "tool-call"
                : toolCall.id().trim();
        return callId + "-" + (taskId == null ? "task" : taskId);
    }

    private String buildLoopRunId(Long taskId, String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            return traceId + "-loop";
        }
        return "task-" + (taskId == null ? "unknown" : taskId) + "-loop";
    }

    private AgentTaskContext loadTaskContext(Long taskId) {
        if (taskId == null || agentRepository == null) {
            return null;
        }
        return agentRepository.findTaskContext(taskId);
    }

    private void publishToolCallStatus(Long projectId,
                                       Long taskId,
                                       Long conversationId,
                                       AgentTaskContext taskContext,
                                       Long turnId,
                                       AgentLlmToolCall toolCall,
                                       int turnIndex,
                                       ToolCallResult toolResult) {
        if (taskRuntimeStatusPublisher == null) {
            return;
        }
        String status = toolResult == null || toolResult.status() == null
                ? "failed"
                : toolResult.status().toLowerCase();
        String toolName = resolveToolDisplayName(toolCall);
        String recoveryCursor = toolResult != null && toolResult.approvalId() != null
                ? "approval:" + toolResult.approvalId()
                : "tool_call:" + (toolCall == null ? "tool" : toolCall.toolCode()) + ":" + (toolCall == null ? "call" : toolCall.id());
        ToolCallStatusView toolCallStatusView = new ToolCallStatusView(
                toolCall == null ? null : toolCall.id(),
                toolCall == null ? null : toolCall.toolCode(),
                toolName,
                status,
                turnIndex,
                toolCall == null ? null : toolCall.argumentsJson(),
                toolResult == null ? null : toolResult.toolOutput(),
                toolResult == null ? null : toolResult.errorMessage()
        );
        syncToolRuntimeSnapshot(projectId, taskId, taskContext, toolCallStatusView, recoveryCursor);
        RuntimeStatusView runtimeStatusView = new RuntimeStatusView(
                taskId,
                conversationId,
                turnId,
                "tool_call",
                toolName,
                toolCallStatusView,
                toolResult == null || toolResult.approvalId() == null ? null : Map.of("approvalId", toolResult.approvalId()),
                null,
                null,
                true,
                "WAITING_APPROVAL".equals(toolResult == null ? null : toolResult.status()) ? "await_approval" : "continue_tool_loop"
        );
        taskRuntimeStatusPublisher.publishToolCall(projectId, runtimeStatusView);
    }

    private void syncToolRuntimeSnapshot(Long projectId,
                                         Long taskId,
                                         AgentTaskContext taskContext,
                                         ToolCallStatusView toolCallStatusView,
                                         String recoveryCursor) {
        if (projectId == null || taskId == null || taskContext == null) {
            return;
        }
        taskContext.setLastRuntimeStatus("tool_call");
        taskContext.setRecoveryCursor(recoveryCursor);
        Map<String, Object> toolCallSnapshot = new LinkedHashMap<>();
        toolCallSnapshot.put("toolCallId", toolCallStatusView.toolCallId());
        toolCallSnapshot.put("toolCode", toolCallStatusView.toolCode());
        toolCallSnapshot.put("toolName", toolCallStatusView.toolName());
        toolCallSnapshot.put("status", toolCallStatusView.status());
        toolCallSnapshot.put("iteration", toolCallStatusView.iteration());
        toolCallSnapshot.put("argumentsPreview", toolCallStatusView.argumentsPreview());
        toolCallSnapshot.put("output", toolCallStatusView.output());
        toolCallSnapshot.put("errorMessage", toolCallStatusView.errorMessage());
        taskContext.setActiveToolCallsSnapshot(AgentTaskRuntimeUpdater.toSnapshotJson(List.of(toolCallSnapshot)));
        int affected = agentRepository.updateGenerationTaskSnapshots(
                projectId,
                taskId,
                taskContext.getTaskProfileJson(),
                taskContext.getPromptPlanJson(),
                taskContext.getContextPackageJson(),
                taskContext.getActiveToolCallsSnapshot(),
                taskContext.getLastRuntimeStatus(),
                taskContext.getRecoveryCursor()
        );
        if (affected != 1) {
            throw new IllegalStateException("Failed to update generation task snapshots");
        }
    }

    private String resolveToolDisplayName(AgentLlmToolCall toolCall) {
        if (toolCall == null || toolCall.toolCode() == null || toolCall.toolCode().isBlank()) {
            return "tool call";
        }
        String toolCode = toolCall.toolCode().trim();
        if ("draft_generation".equals(toolCode)) {
            String argumentsJson = toolCall.argumentsJson();
            if (argumentsJson != null && argumentsJson.contains("\"operation\":\"rewrite\"")) {
                return "改写正文";
            }
            if (argumentsJson != null && argumentsJson.contains("\"operation\":\"revise\"")) {
                return "套用修订";
            }
            return "生成正文";
        }
        AgentToolDescriptor descriptor = toolDefinitionSource == null ? null : toolDefinitionSource.getRequired(toolCode);
        if (descriptor != null && descriptor.presentation() != null
                && descriptor.presentation().displayName() != null
                && !descriptor.presentation().displayName().isBlank()) {
            return descriptor.presentation().displayName();
        }
        return toolCode;
    }
}
