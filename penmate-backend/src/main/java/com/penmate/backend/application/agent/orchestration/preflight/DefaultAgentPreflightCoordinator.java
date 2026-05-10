package com.penmate.backend.application.agent.orchestration.preflight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.prompt.SystemPromptBundle;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class DefaultAgentPreflightCoordinator implements AgentPreflightCoordinator {

    private final AgentLlmGateway agentLlmGateway;
    private final SystemPromptProvider systemPromptProvider;
    private final ObjectMapper objectMapper;

    public DefaultAgentPreflightCoordinator(AgentLlmGateway agentLlmGateway,
                                            SystemPromptProvider systemPromptProvider,
                                            ObjectMapper objectMapper) {
        this.agentLlmGateway = Objects.requireNonNull(agentLlmGateway, "agentLlmGateway");
        this.systemPromptProvider = Objects.requireNonNull(systemPromptProvider, "systemPromptProvider");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public AgentPreflightDecision coordinate(AgentPreflightRequest request) {
        Objects.requireNonNull(request, "request");
        SystemPromptBundle promptBundle = systemPromptProvider.loadBundle("preflight", "default");
        AgentLlmTurnResponse response = agentLlmGateway.generateTurn(new AgentLlmTurnRequest(
                List.of(
                        Map.of("role", "system", "content", promptBundle.assembledPrompt()),
                        Map.of("role", "user", "content", buildUserMessage(request))
                ),
                List.of(),
                "auto"
        ), null);
        String decisionJson = response == null ? null : response.assistantText();
        return parseDecision(decisionJson);
    }

    private String buildUserMessage(AgentPreflightRequest request) {
        return "projectId=" + request.projectId() + "\n"
                + "conversationId=" + request.conversationId() + "\n"
                + "chapterId=" + request.chapterId() + "\n"
                + "userMessage=" + request.userMessage();
    }

    private AgentPreflightDecision parseDecision(String decisionJson) {
        try {
            JsonNode root = objectMapper.readTree(requiredText(decisionJson, "decisionJson"));
            AgentBehaviorType behaviorType = parseBehaviorType(requiredField(root, "behaviorType"));
            String executionPromptProfile = normalizeExecutionPromptProfile(
                    behaviorType,
                    requiredField(root, "executionPromptProfile")
            );
            boolean includeStyleContext = requiredBooleanField(root, "includeStyleContext");
            boolean includeRagContext = requiredBooleanField(root, "includeRagContext");
            boolean includeStoryBibleContext = requiredBooleanField(root, "includeStoryBibleContext");
            if (behaviorType == AgentBehaviorType.STORY_BIBLE_QUERY_CANDIDATE) {
                includeStoryBibleContext = true;
            }
            String reasoningSummary = requiredField(root, "reasoningSummary");
            return new AgentPreflightDecision(
                    behaviorType,
                    executionPromptProfile,
                    includeStyleContext,
                    includeRagContext,
                    includeStoryBibleContext,
                    reasoningSummary,
                    objectMapper.writeValueAsString(root)
            );
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse preflight decision JSON", ex);
        }
    }

    private String normalizeExecutionPromptProfile(AgentBehaviorType behaviorType, String executionPromptProfile) {
        if (behaviorType == AgentBehaviorType.WORLD_BUILD) {
            return "world-build";
        }
        return executionPromptProfile;
    }

    private String requiredField(JsonNode root, String fieldName) {
        JsonNode field = root == null ? null : root.get(fieldName);
        if (field == null || field.isNull() || field.asText().isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return field.asText().trim();
    }

    private AgentBehaviorType parseBehaviorType(String rawValue) {
        try {
            return AgentBehaviorType.valueOf(rawValue.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("behaviorType is invalid: " + rawValue, ex);
        }
    }

    private boolean requiredBooleanField(JsonNode root, String fieldName) {
        JsonNode field = root == null ? null : root.get(fieldName);
        if (field == null || field.isNull()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (!field.isBoolean()) {
            throw new IllegalArgumentException(fieldName + " must be boolean");
        }
        return field.booleanValue();
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
