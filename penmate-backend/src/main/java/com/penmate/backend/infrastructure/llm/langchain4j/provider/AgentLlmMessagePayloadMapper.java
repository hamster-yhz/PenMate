package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentLlmMessagePayloadMapper {

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
        }
        if (message.toolCallId() != null) {
            payload.put("tool_call_id", message.toolCallId());
        }
        return payload;
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
