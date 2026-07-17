package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.context.ContextPackage;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import com.penmate.backend.application.agent.prompt.StructuredPromptBlockFormatter;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

@Component
public class AgentPromptAssembler {

    private final SystemPromptProvider systemPromptProvider;
    private final StructuredPromptBlockFormatter structuredPromptBlockFormatter;

    public AgentPromptAssembler(SystemPromptProvider systemPromptProvider,
                                StructuredPromptBlockFormatter structuredPromptBlockFormatter) {
        this.systemPromptProvider = systemPromptProvider;
        this.structuredPromptBlockFormatter = structuredPromptBlockFormatter;
    }

    public List<Map<String, Object>> buildExecutionMessages(PromptPlan promptPlan,
                                                            ContextPackage contextPackage,
                                                            String userRequest) {
        return toWireMessages(buildExecutionMessages(promptPlan, contextPackage, userRequest, List.of()));
    }

    public List<AgentLlmMessage> buildExecutionMessages(PromptPlan promptPlan,
                                                        ContextPackage contextPackage,
                                                        String userRequest,
                                                        List<AgentLlmMessage> conversationWindow) {
        ContextPackage resolvedContext = Objects.requireNonNull(contextPackage, "contextPackage");
        String contextSystemMessage = buildExecutionContextSystemMessage(
                resolvedContext.styleSnapshot(),
                resolvedContext.storyBibleEntries(),
                resolvedContext.conflicts(),
                resolvedContext.missingContextFlags(),
                resolvedContext.ragRefs()
        );
        String userRequestBlock = structuredPromptBlockFormatter.wrapBlock("user_request", userRequest == null ? "" : userRequest.trim());

        List<AgentLlmMessage> result = new ArrayList<>();
        result.add(AgentLlmMessage.system(promptPlan == null ? "" : promptPlan.assembledPromptPreview()));
        if (!contextSystemMessage.isBlank()) {
            result.add(AgentLlmMessage.system(contextSystemMessage));
        }
        if (conversationWindow != null && !conversationWindow.isEmpty()) {
            result.addAll(conversationWindow);
        }
        result.add(AgentLlmMessage.user(userRequestBlock));
        return List.copyOf(result);
    }

    private String buildExecutionContextSystemMessage(String style,
                                                      List<String> storyBibleEntries,
                                                      List<String> conflicts,
                                                      List<String> missingFlags,
                                                      List<String> ragRefs) {
        StringJoiner contextBuilder = new StringJoiner("\n\n");
        if (style != null && !style.isBlank()) {
            contextBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"style\"", style));
        }
        if (storyBibleEntries != null && !storyBibleEntries.isEmpty()) {
            contextBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"story_bible\"", String.join("\n", storyBibleEntries)));
        }
        if (conflicts != null && !conflicts.isEmpty()) {
            contextBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"conflict\"", String.join("\n", conflicts)));
        }
        if (missingFlags != null && !missingFlags.isEmpty()) {
            contextBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"missing\"", String.join("\n", missingFlags)));
        }
        if (ragRefs != null && !ragRefs.isEmpty()) {
            contextBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"rag\"", String.join("\n", ragRefs)));
        }
        return contextBuilder.toString();
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
