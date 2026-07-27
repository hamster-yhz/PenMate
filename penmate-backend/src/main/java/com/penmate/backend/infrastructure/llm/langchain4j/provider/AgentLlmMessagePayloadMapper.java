package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmProviderItem;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentLlmMessagePayloadMapper {

    static final String CHAT_COMPLETIONS_TOOL_CALL_EXTRA = "OPENAI_CHAT_COMPLETIONS_TOOL_CALL_EXTRA";

    public List<Map<String, Object>> toProviderMessages(List<AgentLlmMessage> messages) {
        return (messages == null ? List.<AgentLlmMessage>of() : messages).stream()
                .map(this::toProviderMessage)
                .toList();
    }

    private Map<String, Object> toProviderMessage(AgentLlmMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", message.role().wireValue());
        payload.put("content", message.content());
        if (!message.toolCalls().isEmpty()) {
            payload.put("tool_calls", message.toolCalls().stream().map(this::toToolCallPayload).toList());
            restoreToolCallExtraContent(payload, message.providerItems());
        }
        if (message.toolCallId() != null) {
            payload.put("tool_call_id", message.toolCallId());
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private void restoreToolCallExtraContent(Map<String, Object> message,
                                             List<AgentLlmProviderItem> providerItems) {
        Object rawToolCalls = message.get("tool_calls");
        if (!(rawToolCalls instanceof List<?> toolCalls)) return;
        for (AgentLlmProviderItem providerItem : providerItems) {
            if (!CHAT_COMPLETIONS_TOOL_CALL_EXTRA.equalsIgnoreCase(providerItem.protocolCode())) continue;
            cn.hutool.json.JSONObject saved = AgentJsonCodec.parseObj(providerItem.payloadJson());
            String toolCallId = saved.getStr("toolCallId", "");
            cn.hutool.json.JSONObject extraContent = saved.getJSONObject("extraContent");
            if (toolCallId.isBlank() || extraContent == null || extraContent.isEmpty()) continue;
            for (Object rawToolCall : toolCalls) {
                if (!(rawToolCall instanceof Map<?, ?> toolCall)
                        || !toolCallId.equals(String.valueOf(toolCall.get("id")))) continue;
                ((Map<String, Object>) toolCall).put("extra_content", new LinkedHashMap<>(extraContent));
                break;
            }
        }
    }

    private Map<String, Object> toToolCallPayload(AgentLlmToolCallPayload toolCall) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", toolCall.functionName());
        function.put("arguments", toolCall.argumentsJson());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", toolCall.id());
        payload.put("type", toolCall.type());
        payload.put("function", function);
        return payload;
    }
}
