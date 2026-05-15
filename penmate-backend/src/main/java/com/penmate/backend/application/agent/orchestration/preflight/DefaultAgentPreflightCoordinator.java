package com.penmate.backend.application.agent.orchestration.preflight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.prompt.StructuredPromptBlockFormatter;
import com.penmate.backend.application.agent.prompt.SystemPromptBundle;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 前置判定协调器，负责把用户请求整理为结构化输入，交给 preflight 模型决定执行画像与上下文路由开关。
 */
@Slf4j
@Component
public class DefaultAgentPreflightCoordinator implements AgentPreflightCoordinator {

    private final AgentLlmGateway agentLlmGateway;
    private final SystemPromptProvider systemPromptProvider;
    private final ObjectMapper objectMapper;
    private final StructuredPromptBlockFormatter structuredPromptBlockFormatter;

    public DefaultAgentPreflightCoordinator(AgentLlmGateway agentLlmGateway,
                                            SystemPromptProvider systemPromptProvider,
                                            ObjectMapper objectMapper,
                                            StructuredPromptBlockFormatter structuredPromptBlockFormatter) {
        this.agentLlmGateway = Objects.requireNonNull(agentLlmGateway, "agentLlmGateway");
        this.systemPromptProvider = Objects.requireNonNull(systemPromptProvider, "systemPromptProvider");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.structuredPromptBlockFormatter = Objects.requireNonNull(structuredPromptBlockFormatter, "structuredPromptBlockFormatter");
    }

    @Override
    public AgentPreflightDecision coordinate(AgentPreflightRequest request) {
        Objects.requireNonNull(request, "request");
        log.info("Agent 前置判定开始: projectId={}, conversationId={}, chapterId={}",
                request.projectId(),
                request.conversationId(),
                request.chapterId());
        SystemPromptBundle promptBundle = systemPromptProvider.loadBundle("preflight", "default");
        AgentLlmTurnResponse response = agentLlmGateway.generateTurn(new AgentLlmTurnRequest(
                List.of(
                        Map.of("role", "system", "content", promptBundle.assembledPrompt()),
                        Map.of("role", "user", "content", buildUserMessage(request))
                ),
                List.of(),
                "auto"
        ), request.executionConfig());
        String decisionJson = response == null ? null : response.assistantText();
        AgentPreflightDecision decision = parseDecision(decisionJson);
        log.info("Agent 前置判定完成: behaviorType={}, executionProfile={}, includeStyleContext={}, includeRagContext={}, includeStoryBibleContext={}, storyBibleFlag={}, ragFlag={}, approvalFlag={}",
                decision.behaviorType(),
                decision.executionPromptProfile(),
                decision.includeStyleContext(),
                decision.includeRagContext(),
                decision.includeStoryBibleContext(),
                decision.includeStoryBibleContext(),
                decision.includeRagContext(),
                decision.needsApproval());
        return decision;
    }

    private String buildUserMessage(AgentPreflightRequest request) {
        String preflightContext = "<project_id>" + request.projectId() + "</project_id>\n"
                + "<conversation_id>" + request.conversationId() + "</conversation_id>\n"
                + "<chapter_id>" + request.chapterId() + "</chapter_id>";
        return structuredPromptBlockFormatter.wrapBlock("context type=\"preflight\"", preflightContext)
                + "\n\n"
                + structuredPromptBlockFormatter.wrapBlock("user_request", request.userMessage());
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
            List<String> intentTags = optionalStringList(root, "intentTags");
            List<String> hardConstraints = optionalStringList(root, "hardConstraints");
            List<String> enabledSkills = optionalStringList(root, "enabledSkills");
            List<String> enabledTools = optionalStringList(root, "enabledTools");
            String outputExpectation = optionalField(root, "outputExpectation");
            boolean needsApproval = optionalBooleanField(root, "needsApproval");
            boolean needsStoryBibleUpdate = optionalBooleanField(root, "needsStoryBibleUpdate");
            boolean needsClarification = optionalBooleanField(root, "needsClarification");
            if (behaviorType == AgentBehaviorType.STORY_BIBLE_QUERY_CANDIDATE) {
                includeStoryBibleContext = true;
            }
            if (needsClarification && !intentTags.contains("CLARIFICATION")) {
                intentTags = new ArrayList<>(intentTags);
                intentTags.add("CLARIFICATION");
            }
            String reasoningSummary = requiredField(root, "reasoningSummary");
            return new AgentPreflightDecision(
                    behaviorType,
                    executionPromptProfile,
                    includeStyleContext,
                    includeRagContext,
                    includeStoryBibleContext,
                    reasoningSummary,
                    objectMapper.writeValueAsString(root),
                    intentTags,
                    hardConstraints,
                    enabledSkills,
                    enabledTools,
                    outputExpectation,
                    needsApproval,
                    needsStoryBibleUpdate,
                    needsClarification
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

    private List<String> optionalStringList(JsonNode root, String fieldName) {
        JsonNode field = root == null ? null : root.get(fieldName);
        if (field == null || field.isNull()) {
            return List.of();
        }
        if (!field.isArray()) {
            throw new IllegalArgumentException(fieldName + " must be array");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : field) {
            if (item == null || item.isNull() || item.asText().isBlank()) {
                throw new IllegalArgumentException(fieldName + " must contain non-blank strings");
            }
            values.add(item.asText().trim());
        }
        return List.copyOf(values);
    }

    private String optionalField(JsonNode root, String fieldName) {
        JsonNode field = root == null ? null : root.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        if (field.asText().isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return field.asText().trim();
    }

    private boolean optionalBooleanField(JsonNode root, String fieldName) {
        JsonNode field = root == null ? null : root.get(fieldName);
        if (field == null || field.isNull()) {
            return false;
        }
        if (!field.isBoolean()) {
            throw new IllegalArgumentException(fieldName + " must be boolean");
        }
        return field.booleanValue();
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
