package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.prompt.PromptContextRenderer;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentPromptAssembler {

    private final PromptContextRenderer contextRenderer;

    public AgentPromptAssembler(PromptContextRenderer contextRenderer) {
        this.contextRenderer = contextRenderer;
    }

    public List<Map<String, Object>> buildExecutionMessages(PromptPlan promptPlan,
                                                             String userRequest) {
        return toWireMessages(buildExecutionMessages(promptPlan, userRequest, List.of()));
    }

    public List<AgentLlmMessage> buildExecutionMessages(PromptPlan promptPlan,
                                                         String userRequest,
                                                         List<AgentLlmMessage> conversationWindow) {
        return buildExecutionMessages(promptPlan, "", userRequest, conversationWindow);
    }

    public List<AgentLlmMessage> buildExecutionMessages(PromptPlan promptPlan,
                                                         String activatedSkills,
                                                         String userRequest,
                                                         List<AgentLlmMessage> conversationWindow) {
        List<AgentLlmMessage> result = new ArrayList<>();
        if (promptPlan != null && !promptPlan.stablePrefix().isBlank()) {
            result.add(AgentLlmMessage.system(promptPlan.stablePrefix()));
        }
        if (activatedSkills != null && !activatedSkills.isBlank()) {
            result.add(AgentLlmMessage.system(activatedSkills.trim()));
        }
        if (promptPlan != null && !promptPlan.dynamicContext().isBlank()) {
            result.add(AgentLlmMessage.system(promptPlan.dynamicContext()));
        }
        if (conversationWindow != null && !conversationWindow.isEmpty()) {
            result.addAll(conversationWindow);
        }
        result.add(AgentLlmMessage.user(contextRenderer.renderUserRequest(userRequest)));
        return List.copyOf(result);
    }

    private List<Map<String, Object>> toWireMessages(List<AgentLlmMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .map(this::toWireMessage)
                .toList();
    }

    private Map<String, Object> toWireMessage(AgentLlmMessage message) {
        Map<String, Object> wireMessage = new LinkedHashMap<>();
        wireMessage.put("role", message.role().wireValue());
        wireMessage.put("content", message.content());
        if (message.toolCallId() != null) {
            wireMessage.put("tool_call_id", message.toolCallId());
        }
        if (!message.toolCalls().isEmpty()) {
            wireMessage.put("tool_calls", message.toolCalls().stream()
                    .map(this::toWireToolCall)
                    .toList());
        }
        return wireMessage;
    }

    private Map<String, Object> toWireToolCall(AgentLlmToolCallPayload toolCall) {
        Map<String, Object> wireToolCall = new LinkedHashMap<>();
        wireToolCall.put("id", toolCall.id());
        wireToolCall.put("type", toolCall.type());
        wireToolCall.put("function", Map.of(
                "name", toolCall.functionName(),
                "arguments", toolCall.argumentsJson()
        ));
        return wireToolCall;
    }

}
