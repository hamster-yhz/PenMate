package com.penmate.backend.application.agent.tool.runtime;

import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import com.penmate.backend.domain.agent.model.AgentLlmProviderItem;
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

    private final JsonCodec jsonCodec;

    public ToolCallSnapshotMapper(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

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
                        .toList(),
                response.providerItems()
        );
    }

    public String toAssistantToolCallsJson(List<AgentLlmToolCall> toolCalls) {
        return jsonCodec.write(buildToolCallPayloads(toolCalls));
    }

    public String toConversationMessagesJson(List<AgentLlmMessage> messages) {
        return jsonCodec.write(messages.stream().map(this::toMessagePayload).toList());
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
        if (!message.providerItems().isEmpty()) {
            payload.put("provider_items", message.providerItems());
        }
        return payload;
    }

    public List<AgentLlmMessage> parseMessagesToTyped(String raw) {
        List<AgentLlmMessage> messages = new ArrayList<>();
        for (Object item : readList(raw)) {
            if (item instanceof Map<?, ?> map) {
                messages.add(toAgentLlmMessage(toStringKeyMap(map)));
            }
        }
        return messages;
    }

    public List<Map<String, Object>> parseMessages(String raw) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Object item : readList(raw)) {
            if (item instanceof Map<?, ?> map) {
                messages.add(toStringKeyMap(map));
            }
        }
        return messages;
    }

    public List<Map<String, Object>> parseToolCallPayloads(String raw) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (Object item : readList(raw)) {
            if (item instanceof Map<?, ?> map) {
                payloads.add(toStringKeyMap(map));
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
            return AgentLlmMessage.assistant(content,
                    toToolCallPayloadModels(rawMessage.get("tool_calls")),
                    toProviderItems(rawMessage.get("provider_items")));
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
        if (value instanceof Map<?, ?> map) {
            return toStringKeyMap(map);
        }
        return Map.of();
    }

    private List<AgentLlmProviderItem> toProviderItems(Object value) {
        List<AgentLlmProviderItem> items = new ArrayList<>();
        if (!(value instanceof List<?> values)) return items;
        for (Object item : values) {
            Map<String, Object> payload = mapValue(item);
            String protocolCode = stringValue(payload.get("protocolCode"));
            String payloadJson = stringValue(payload.get("payloadJson"));
            if (protocolCode != null && payloadJson != null) {
                items.add(new AgentLlmProviderItem(protocolCode, payloadJson));
            }
        }
        return items;
    }

    private List<Object> readList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return jsonCodec.readList(raw, Object.class);
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
