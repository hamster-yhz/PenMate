package com.penmate.backend.application.agent.loop;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.StaticToolMetadataRegistry;
import com.penmate.backend.application.agent.ToolInvocationGateway;
import com.penmate.backend.application.agent.ToolInvocationGatewayResult;
import com.penmate.backend.application.agent.ToolInvocationRequest;
import com.penmate.backend.application.agent.json.AgentJsons;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Agent 真实 tool-calling loop controller。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolLoopController {

    private static final int MAX_TOOL_TURNS = 4;
    private static final int MAX_TOOL_CALLS_PER_TURN = 3;

    private final AgentLlmGateway agentLlmGateway;
    private final ToolInvocationGateway toolInvocationGateway;
    private final StaticToolMetadataRegistry staticToolMetadataRegistry;
    private final com.penmate.backend.domain.agent.repository.AgentRepository agentRepository;
    private final AgentModelRoutingService agentModelRoutingService;

    public AgentToolLoopIterationResult execute(Long projectId,
                                                Long taskId,
                                                Long conversationId,
                                                Long operatorId,
                                                String traceId,
                                                List<Map<String, Object>> initialMessages,
                                                AgentLlmExecutionConfig executionConfig) {
        List<Map<String, Object>> messages = new ArrayList<>(initialMessages == null ? List.of() : initialMessages);
        List<AgentLlmToolSchema> tools = staticToolMetadataRegistry.toLlmToolSchemas();
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

            messages.add(buildAssistantToolCallMessage(response));
            String assistantToolCallsJson = AgentJsons.toJson(buildToolCallPayloads(response.toolCalls()));
            String loopRunId = buildLoopRunId(taskId, traceId);
            for (AgentLlmToolCall toolCall : response.toolCalls()) {
                totalToolCalls += 1;
                ToolInvocationGatewayResult toolResult = toolInvocationGateway.invoke(new ToolInvocationRequest(
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
                        AgentJsons.toJson(messages),
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

    public ToolInvocationGatewayResult resumeFromPending(ApprovalRequest request, PendingToolInvocationSnapshot snapshot) {
        List<Map<String, Object>> messages = parseMessages(snapshot.conversationMessagesJson());
        StringBuilder toolContextBuilder = new StringBuilder();

        ToolInvocationGatewayResult approvedToolResult = toolInvocationGateway.resume(snapshot);
        if (!"SUCCESS".equals(approvedToolResult.status())) {
            return approvedToolResult;
        }
        appendToolResultMessage(messages, toolContextBuilder, snapshot.toolCallId(), approvedToolResult.toolOutput());

        List<Map<String, Object>> pendingToolCalls = parseToolCallPayloads(snapshot.assistantToolCallsJson());
        int approvedCallIndex = resolveToolCallIndex(pendingToolCalls, snapshot.toolCallId());
        for (int index = approvedCallIndex + 1; index < pendingToolCalls.size(); index++) {
            Map<String, Object> toolCallPayload = pendingToolCalls.get(index);
            ToolInvocationGatewayResult toolResult = toolInvocationGateway.invoke(buildLoopResumeRequest(snapshot, toolCallPayload, messages));
            if ("WAITING_APPROVAL".equals(toolResult.status())) {
                return toolResult;
            }
            if (!"SUCCESS".equals(toolResult.status())) {
                return toolResult;
            }
            appendToolResultMessage(messages, toolContextBuilder, stringValue(toolCallPayload.get("id")), toolResult.toolOutput());
        }

        AgentLlmExecutionConfig executionConfig = resolveExecutionConfig(snapshot);
        for (int turnIndex = 0; turnIndex < MAX_TOOL_TURNS; turnIndex++) {
            AgentLlmTurnResponse response = agentLlmGateway.generateTurn(
                    new AgentLlmTurnRequest(messages, staticToolMetadataRegistry.toLlmToolSchemas(), "auto"),
                    executionConfig
            );
            if ("tool_calls".equalsIgnoreCase(response.finishReason()) && response.toolCalls().isEmpty()) {
                throw new IllegalStateException("LLM finishReason=tool_calls but toolCalls is empty");
            }
            if (!response.requestsToolCalls()) {
                return ToolInvocationGatewayResult.success(response.assistantText());
            }
            ensureToolCallsPerTurnWithinLimit(response.toolCalls());
            messages.add(buildAssistantToolCallMessage(response));
            String assistantToolCallsJson = AgentJsons.toJson(buildToolCallPayloads(response.toolCalls()));
            for (AgentLlmToolCall toolCall : response.toolCalls()) {
                ToolInvocationGatewayResult toolResult = toolInvocationGateway.invoke(new ToolInvocationRequest(
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
                        AgentJsons.toJson(messages),
                        "RESUME_LOOP",
                        snapshot.approvalSummaryJson()
                ));
                if ("WAITING_APPROVAL".equals(toolResult.status())) {
                    return toolResult;
                }
                if (!"SUCCESS".equals(toolResult.status())) {
                    return toolResult;
                }
                appendToolResultMessage(messages, toolContextBuilder, toolCall.id(), toolResult.toolOutput());
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

    private Map<String, Object> buildAssistantToolCallMessage(AgentLlmTurnResponse response) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", response.assistantText());
        message.put("tool_calls", buildToolCallPayloads(response.toolCalls()));
        return message;
    }

    private List<Map<String, Object>> buildToolCallPayloads(List<AgentLlmToolCall> toolCalls) {
        return toolCalls.stream().map(this::toToolCallPayload).toList();
    }

    private Map<String, Object> toToolCallPayload(AgentLlmToolCall toolCall) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", toolCall.toolCode());
        function.put("arguments", toolCall.argumentsJson());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", toolCall.id());
        payload.put("type", "function");
        payload.put("function", function);
        return payload;
    }

    private Map<String, Object> buildToolResultMessage(String toolCallId, String toolOutput) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", toolCallId);
        message.put("content", toolOutput == null ? "" : toolOutput);
        return message;
    }

    private String extractToolOutput(ToolInvocationGatewayResult toolResult, AgentLlmToolCall toolCall) {
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

    private List<Map<String, Object>> parseMessages(String raw) {
        JSONArray array = AgentJsons.parseArray(raw);
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Object item : array) {
            if (item instanceof JSONObject object) {
                messages.add(new LinkedHashMap<>(object));
            } else if (item instanceof Map<?, ?> map) {
                Map<String, Object> message = new LinkedHashMap<>();
                map.forEach((key, value) -> message.put(String.valueOf(key), value));
                messages.add(message);
            }
        }
        return messages;
    }

    private List<Map<String, Object>> parseToolCallPayloads(String raw) {
        JSONArray array = AgentJsons.parseArray(raw);
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (Object item : array) {
            if (item instanceof JSONObject object) {
                payloads.add(new LinkedHashMap<>(object));
            } else if (item instanceof Map<?, ?> map) {
                Map<String, Object> payload = new LinkedHashMap<>();
                map.forEach((key, value) -> payload.put(String.valueOf(key), value));
                payloads.add(payload);
            }
        }
        return payloads;
    }

    private int resolveToolCallIndex(List<Map<String, Object>> pendingToolCalls, String toolCallId) {
        for (int index = 0; index < pendingToolCalls.size(); index++) {
            if (Objects.equals(toolCallId, stringValue(pendingToolCalls.get(index).get("id")))) {
                return index;
            }
        }
        return -1;
    }

    private ToolInvocationRequest buildLoopResumeRequest(PendingToolInvocationSnapshot snapshot,
                                                         Map<String, Object> toolCallPayload,
                                                         List<Map<String, Object>> messages) {
        Map<String, Object> functionPayload = mapValue(toolCallPayload.get("function"));
        String toolCallId = stringValue(toolCallPayload.get("id"));
        String toolCode = stringValue(functionPayload.get("name"));
        String toolArgsJson = stringValue(functionPayload.get("arguments"));
        AgentLlmToolCall toolCall = new AgentLlmToolCall(toolCallId, toolCode, toolArgsJson);
        return new ToolInvocationRequest(
                snapshot.projectId(),
                snapshot.taskId(),
                snapshot.conversationId(),
                toolCode,
                toolArgsJson,
                snapshot.operatorId(),
                snapshot.traceId(),
                snapshot.contextJson(),
                buildIdempotencyKey(snapshot.taskId(), toolCall),
                snapshot.loopRunId(),
                snapshot.llmTurnIndex(),
                toolCallId,
                snapshot.assistantToolCallsJson(),
                AgentJsons.toJson(messages),
                "RESUME_LOOP",
                snapshot.approvalSummaryJson()
        );
    }

    private void appendToolResultMessage(List<Map<String, Object>> messages,
                                         StringBuilder toolContextBuilder,
                                         String toolCallId,
                                         String toolOutput) {
        String safeOutput = toolOutput == null ? "" : toolOutput;
        appendToolContext(toolContextBuilder, safeOutput);
        messages.add(buildToolResultMessage(toolCallId, safeOutput));
    }

    private AgentLlmExecutionConfig resolveExecutionConfig(PendingToolInvocationSnapshot snapshot) {
        AgentGenerationTask task = agentRepository.findGenerationTask(snapshot.projectId(), snapshot.taskId());
        if (task == null || task.getModelConfigId() == null) {
            throw new IllegalStateException("Generation task missing execution config for approval resume");
        }
        return agentModelRoutingService.resolveExecutionConfig(snapshot.projectId(), task.getModelConfigId(), snapshot.traceId());
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof JSONObject object) {
            return new LinkedHashMap<>(object);
        }
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
