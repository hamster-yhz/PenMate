package com.penmate.backend.application.agent.tool.runtime;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
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

    public AgentLlmMessage buildAssistantToolCallMessage(AgentLlmTurnResponse response) {
        return AgentLlmMessage.assistant(
                response.assistantText(),
                response.toolCalls().stream()
                        .map(toolCall -> new AgentLlmToolCallPayload(
                                toolCall.id(),
                                "function",
                                toolCall.toolCode(),
                                toolCall.argumentsJson()
                        ))
                        .toList()
        );
    }

    public String toAssistantToolCallsJson(List<AgentLlmToolCall> toolCalls) {
        return AgentJsonCodec.toJson(buildToolCallPayloads(toolCalls));
    }

    public String toConversationMessagesJson(List<AgentLlmMessage> messages) {
        return AgentJsonCodec.toJson(messages.stream().map(this::toMessagePayload).toList());
    }

    private Map<String, Object> toMessagePayload(AgentLlmMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", message.role().wireValue());
        payload.put("content", message.content());
        if (!message.toolCalls().isEmpty()) {
            payload.put("tool_calls", message.toolCalls().stream().map(toolCall -> {
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", toolCall.functionName());
                function.put("arguments", toolCall.argumentsJson());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", toolCall.id());
                item.put("type", toolCall.type());
                item.put("function", function);
                return item;
            }).toList());
        }
        if (message.toolCallId() != null) {
            payload.put("tool_call_id", message.toolCallId());
        }
        return payload;
    }

    public List<AgentLlmMessage> parseMessagesToTyped(String raw) {
        JSONArray array = AgentJsonCodec.parseArray(raw);
        List<AgentLlmMessage> messages = new ArrayList<>();
        for (Object item : array) {
            Map<String, Object> payload;
            if (item instanceof JSONObject object) {
                payload = new LinkedHashMap<>(object);
            } else if (item instanceof Map<?, ?> map) {
                payload = new LinkedHashMap<>();
                map.forEach((key, value) -> payload.put(String.valueOf(key), value));
            } else {
                continue;
            }
            messages.add(toAgentLlmMessage(payload));
        }
        return messages;
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

    public List<AgentLlmMessage> toAgentLlmMessages(List<Map<String, Object>> rawMessages) {
        List<AgentLlmMessage> messages = new ArrayList<>();
        for (Map<String, Object> rawMessage : rawMessages == null ? List.<Map<String, Object>>of() : rawMessages) {
            messages.add(toAgentLlmMessage(rawMessage));
        }
        return messages;
    }

    public AgentLlmMessage toAgentLlmMessage(Map<String, Object> rawMessage) {
        String role = stringValue(rawMessage.get("role"));
        String content = stringValue(rawMessage.get("content"));
        if ("system".equalsIgnoreCase(role)) {
            return AgentLlmMessage.system(content);
        }
        if ("user".equalsIgnoreCase(role)) {
            return AgentLlmMessage.user(content);
        }
        if ("assistant".equalsIgnoreCase(role)) {
            return AgentLlmMessage.assistant(content, toToolCallPayloadModels(rawMessage.get("tool_calls")));
        }
        if ("tool".equalsIgnoreCase(role)) {
            return AgentLlmMessage.tool(stringValue(rawMessage.get("tool_call_id")), content);
        }
        throw new IllegalArgumentException("Unsupported llm message role: " + role);
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

    private List<AgentLlmToolCallPayload> toToolCallPayloadModels(Object value) {
        List<AgentLlmToolCallPayload> payloads = new ArrayList<>();
        if (value instanceof List<?> items) {
            for (Object item : items) {
                Map<String, Object> payload = mapValue(item);
                Map<String, Object> function = mapValue(payload.get("function"));
                payloads.add(new AgentLlmToolCallPayload(
                        stringValue(payload.get("id")),
                        stringValue(payload.get("type")),
                        stringValue(function.get("name")),
                        stringValue(function.get("arguments"))
                ));
            }
        }
        return payloads;
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
