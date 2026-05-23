package com.penmate.backend.application.agent.tool.runtime;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 待审批 tool 调用恢复服务。
 * <p>当人工审批通过后，该服务负责基于 {@link PendingToolInvocationSnapshot} 还原当时的消息上下文、先补执行被挂起的 tool，
 * 再决定是否继续消费同一批剩余 tool calls，最后在必要时重新进入后续 LLM tool loop。</p>
 * <p>它是“审批通过 → 原调用恢复 → loop 续跑”这段协议的专用运行时组件，而不是新的业务用例入口。</p>
 */
@Component
@RequiredArgsConstructor
public class ToolCallResumeService {

    private static final int MAX_TOOL_TURNS = 4;
    private static final int MAX_TOOL_CALLS_PER_TURN = 3;

    private final ToolCallExecutionService toolCallExecutionService;
    private final AgentLlmGateway agentLlmGateway;
    private final AgentToolDefinitionSource toolDefinitionSource;
    private final AgentRepository agentRepository;
    private final AgentModelRoutingService agentModelRoutingService;
    private final ToolCallSnapshotMapper toolCallSnapshotMapper;

    public ToolCallResult resumeFromPending(ApprovalRequest request, PendingToolInvocationSnapshot snapshot) {
        List<AgentLlmMessage> messages = new ArrayList<>(toolCallSnapshotMapper.parseMessagesToTyped(snapshot.conversationMessagesJson()));

        ToolCallResult approvedToolResult = toolCallExecutionService.execute(new ToolCallRequest(
                snapshot.projectId(),
                snapshot.taskId(),
                snapshot.conversationId(),
                snapshot.toolCode(),
                snapshot.toolArgsJson(),
                snapshot.operatorId(),
                snapshot.traceId(),
                snapshot.contextJson(),
                snapshot.idempotencyKey(),
                snapshot.loopRunId(),
                snapshot.llmTurnIndex(),
                snapshot.toolCallId(),
                snapshot.assistantToolCallsJson(),
                snapshot.conversationMessagesJson(),
                snapshot.resumeMode(),
                snapshot.approvalSummaryJson()
        ));
        if ("WAITING_APPROVAL".equals(approvedToolResult.status())) {
            throw new IllegalStateException("approved tool invocation cannot require approval again: toolCode=" + snapshot.toolCode());
        }
        if (!"SUCCESS".equals(approvedToolResult.status())) {
            return approvedToolResult;
        }
        appendToolResultMessage(messages, snapshot.toolCallId(), approvedToolResult.toolOutput());

        List<Map<String, Object>> pendingToolCalls = toolCallSnapshotMapper.parseToolCallPayloads(snapshot.assistantToolCallsJson());
        int approvedCallIndex = resolveToolCallIndex(pendingToolCalls, snapshot.toolCallId());
        for (int index = approvedCallIndex + 1; index < pendingToolCalls.size(); index++) {
            Map<String, Object> toolCallPayload = pendingToolCalls.get(index);
            String toolCallId = stringValue(toolCallPayload.get("id"));
            String toolCode = stringValue(mapValue(toolCallPayload.get("function")).get("name"));
            String toolArgsJson = stringValue(mapValue(toolCallPayload.get("function")).get("arguments"));
            ToolCallResult toolResult = toolCallExecutionService.execute(toolCallSnapshotMapper.buildLoopResumeRequest(
                    snapshot,
                    toolCallPayload,
                    messages,
                    buildIdempotencyKey(snapshot.taskId(), new AgentLlmToolCall(toolCallId, toolCode, toolArgsJson))
            ));
            if ("WAITING_APPROVAL".equals(toolResult.status())) {
                return toolResult;
            }
            if (!"SUCCESS".equals(toolResult.status())) {
                return toolResult;
            }
            appendToolResultMessage(messages, toolCallId, toolResult.toolOutput());
        }

        AgentLlmExecutionConfig executionConfig = resolveExecutionConfig(snapshot);
        for (int turnIndex = 0; turnIndex < MAX_TOOL_TURNS; turnIndex++) {
            AgentLlmTurnResponse response = agentLlmGateway.generateTurn(
                    new AgentLlmTurnRequest(
                            messages,
                            toolDefinitionSource.listLlmSchemas(),
                            "auto"
                    ),
                    executionConfig
            );
            if ("tool_calls".equalsIgnoreCase(response.finishReason()) && response.toolCalls().isEmpty()) {
                throw new IllegalStateException("LLM finishReason=tool_calls but toolCalls is empty");
            }
            if (!response.requestsToolCalls()) {
                return ToolCallResult.success(response.assistantText());
            }
            ensureToolCallsPerTurnWithinLimit(response.toolCalls());
            messages.add(toolCallSnapshotMapper.buildAssistantToolCallMessage(response));
            String assistantToolCallsJson = toolCallSnapshotMapper.toAssistantToolCallsJson(response.toolCalls());
            for (AgentLlmToolCall toolCall : response.toolCalls()) {
                ToolCallResult toolResult = toolCallExecutionService.execute(new ToolCallRequest(
                        snapshot.projectId(),
                        snapshot.taskId(),
                        snapshot.conversationId(),
                        toolCall.toolCode(),
                        toolCall.argumentsJson(),
                        snapshot.operatorId(),
                        snapshot.traceId(),
                        snapshot.contextJson(),
                        buildIdempotencyKey(snapshot.taskId(), toolCall),
                        snapshot.loopRunId(),
                        snapshot.llmTurnIndex() == null ? turnIndex : snapshot.llmTurnIndex() + turnIndex + 1,
                        toolCall.id(),
                        assistantToolCallsJson,
                        toolCallSnapshotMapper.toConversationMessagesJson(messages),
                        "RESUME_LOOP",
                        snapshot.approvalSummaryJson()
                ));
                if ("WAITING_APPROVAL".equals(toolResult.status())) {
                    return toolResult;
                }
                if (!"SUCCESS".equals(toolResult.status())) {
                    return toolResult;
                }
                appendToolResultMessage(messages, toolCall.id(), toolResult.toolOutput());
            }
        }
        throw new IllegalStateException("Agent tool loop exceeded max turns: " + MAX_TOOL_TURNS);
    }

    private void ensureToolCallsPerTurnWithinLimit(List<AgentLlmToolCall> toolCalls) {
        int count = toolCalls == null ? 0 : toolCalls.size();
        if (count > MAX_TOOL_CALLS_PER_TURN) {
            throw new IllegalStateException("Agent tool loop exceeded max tool calls per turn: " + MAX_TOOL_CALLS_PER_TURN);
        }
    }

    private void appendToolResultMessage(List<AgentLlmMessage> messages,
                                         String toolCallId,
                                         String toolOutput) {
        String safeOutput = toolOutput == null ? "" : toolOutput;
        messages.add(AgentLlmMessage.tool(toolCallId, safeOutput));
    }

    private int resolveToolCallIndex(List<Map<String, Object>> pendingToolCalls, String toolCallId) {
        for (int index = 0; index < pendingToolCalls.size(); index++) {
            if (Objects.equals(toolCallId, stringValue(pendingToolCalls.get(index).get("id")))) {
                return index;
            }
        }
        return -1;
    }

    private AgentLlmExecutionConfig resolveExecutionConfig(PendingToolInvocationSnapshot snapshot) {
        AgentGenerationTask task = agentRepository.findGenerationTask(snapshot.projectId(), snapshot.taskId());
        if (task == null || task.getModelConfigId() == null || task.getUserId() == null) {
            throw new IllegalStateException("Generation task missing execution config for approval resume");
        }
        return agentModelRoutingService.resolveExecutionConfig(task.getUserId(), task.getModelConfigId(), snapshot.traceId());
    }

    private String buildIdempotencyKey(Long taskId, AgentLlmToolCall toolCall) {
        String callId = toolCall == null || toolCall.id() == null || toolCall.id().isBlank()
                ? "tool-call"
                : toolCall.id().trim();
        return callId + "-" + (taskId == null ? "task" : taskId);
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> payload = new LinkedHashMap<>();
            map.forEach((key, item) -> payload.put(String.valueOf(key), item));
            return payload;
        }
        return Map.of();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
