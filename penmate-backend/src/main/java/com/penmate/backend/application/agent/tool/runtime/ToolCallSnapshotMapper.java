package com.penmate.backend.application.agent.tool.runtime;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * tool 调用快照映射器。
 * <p>负责在 LLM tool-call 结构、对话消息列表与待审批快照可持久化 JSON 之间做双向转换，服务于审批挂起与恢复流程。</p>
 */
@Component
public class ToolCallSnapshotMapper {

    public Map<String, Object> buildAssistantToolCallMessage(AgentLlmTurnResponse response) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", response.assistantText());
        message.put("tool_calls", buildToolCallPayloads(response.toolCalls()));
        return message;
    }

    public String toAssistantToolCallsJson(List<AgentLlmToolCall> toolCalls) {
        return AgentJsonCodec.toJson(buildToolCallPayloads(toolCalls));
    }

    public String toConversationMessagesJson(List<Map<String, Object>> messages) {
        return AgentJsonCodec.toJson(messages);
    }

    public List<Map<String, Object>> parseMessages(String raw) {
        JSONArray array = AgentJsonCodec.parseArray(raw);
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

    public List<Map<String, Object>> parseToolCallPayloads(String raw) {
        JSONArray array = AgentJsonCodec.parseArray(raw);
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

    public ToolCallRequest buildLoopResumeRequest(PendingToolInvocationSnapshot snapshot,
                                                        Map<String, Object> toolCallPayload,
                                                        List<Map<String, Object>> messages,
                                                        String idempotencyKey) {
        Map<String, Object> functionPayload = mapValue(toolCallPayload.get("function"));
        String toolCallId = stringValue(toolCallPayload.get("id"));
        String toolCode = stringValue(functionPayload.get("name"));
        String toolArgsJson = stringValue(functionPayload.get("arguments"));
        return new ToolCallRequest(
                snapshot.projectId(),
                snapshot.taskId(),
                snapshot.conversationId(),
                toolCode,
                toolArgsJson,
                snapshot.operatorId(),
                snapshot.traceId(),
                snapshot.contextJson(),
                idempotencyKey,
                snapshot.loopRunId(),
                snapshot.llmTurnIndex(),
                toolCallId,
                snapshot.assistantToolCallsJson(),
                toConversationMessagesJson(messages),
                "RESUME_LOOP",
                snapshot.approvalSummaryJson()
        );
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
