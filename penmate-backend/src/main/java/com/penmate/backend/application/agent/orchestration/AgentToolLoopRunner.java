package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.tool.catalog.StaticAgentToolCatalog;
import com.penmate.backend.application.agent.tool.gateway.ToolCallApplicationService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResumeService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallSnapshotMapper;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 真实 tool-calling loop runner。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolLoopRunner {

    private static final int MAX_TOOL_TURNS = 4;
    private static final int MAX_TOOL_CALLS_PER_TURN = 3;

    private final AgentLlmGateway agentLlmGateway;
    private final ToolCallApplicationService toolCallApplicationService;
    private final StaticAgentToolCatalog staticAgentToolCatalog;
    private final ToolCallResumeService toolCallResumeService;
    private final ToolCallSnapshotMapper toolCallSnapshotMapper;

    public AgentToolLoopIterationResult execute(Long projectId,
                                                Long taskId,
                                                Long conversationId,
                                                Long operatorId,
                                                String traceId,
                                                List<Map<String, Object>> initialMessages,
                                                AgentLlmExecutionConfig executionConfig) {
        List<Map<String, Object>> messages = new ArrayList<>(initialMessages == null ? List.of() : initialMessages);
        List<AgentLlmToolSchema> tools = staticAgentToolCatalog.toLlmToolSchemas();
        StringBuilder toolContextBuilder = new StringBuilder();
        int totalToolCalls = 0;

        for (int turnIndex = 0; turnIndex < MAX_TOOL_TURNS; turnIndex++) {
            AgentLlmTurnResponse response = agentLlmGateway.generateTurn(
                    new AgentLlmTurnRequest(messages, tools, "auto"),
                    executionConfig
            );
            if ("tool_calls".equalsIgnoreCase(response.finishReason()) && response.toolCalls().isEmpty()) {
                throw new IllegalStateException("LLM finishReason=tool_calls but toolCalls is empty");
            }
            if (!response.requestsToolCalls()) {
                return AgentToolLoopIterationResult.completed(
                        response.assistantText(),
                        totalToolCalls,
                        toolContextBuilder.toString()
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
                if ("WAITING_APPROVAL".equals(toolResult.status())) {
                    if (toolResult.approvalId() == null) {
                        throw new IllegalStateException("WAITING_APPROVAL result missing approvalId");
                    }
                    return AgentToolLoopIterationResult.waitingApproval(
                            toolResult.approvalId(),
                            totalToolCalls,
                            toolContextBuilder.toString()
                    );
                }

                String toolOutput = extractToolOutput(toolResult, toolCall);
                appendToolContext(toolContextBuilder, toolOutput);
                messages.add(buildToolResultMessage(toolCall.id(), toolOutput));
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

    private Map<String, Object> buildToolResultMessage(String toolCallId, String toolOutput) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", toolCallId);
        message.put("content", toolOutput == null ? "" : toolOutput);
        return message;
    }

    private String extractToolOutput(ToolCallResult toolResult, AgentLlmToolCall toolCall) {
        if (toolResult == null) {
            throw new IllegalStateException("Tool invocation result is null");
        }
        if (!"SUCCESS".equals(toolResult.status())) {
            log.warn("agent.tool.loop.tool.failed: toolCode={}, status={}, errorCode={}, errorMessage={}",
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
}
