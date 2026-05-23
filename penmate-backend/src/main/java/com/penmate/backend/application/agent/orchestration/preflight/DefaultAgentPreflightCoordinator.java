package com.penmate.backend.application.agent.orchestration.preflight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.orchestration.profile.TaskIntentTag;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.application.agent.prompt.StructuredPromptBlockFormatter;
import com.penmate.backend.application.agent.prompt.SystemPromptBundle;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 前置判定协调器，负责把用户请求整理为结构化输入，交给 preflight 模型决定执行画像与上下文路由开关。
 */
@Slf4j
@Component
public class DefaultAgentPreflightCoordinator implements AgentPreflightCoordinator {

    private static final String PREFLIGHT_DECISION_TOOL_CODE = "submit_preflight_decision";
    private static final String PREFLIGHT_DECISION_TOOL_DESCRIPTION = "Return the preflight decision as structured Json only.";

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
                        AgentLlmMessage.system(promptBundle.assembledPrompt()),
                        AgentLlmMessage.user(buildUserMessage(request))
                ),
                List.of(buildPreflightDecisionToolSchema()),
                "required"
        ), request.executionConfig());
        String decisionJson = extractDecisionJson(response);
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

    private String extractDecisionJson(AgentLlmTurnResponse response) {
        if (response == null) {
            return null;
        }
        if (response.requestsToolCalls()) {
            return response.toolCalls().stream()
                    .filter(toolCall -> PREFLIGHT_DECISION_TOOL_CODE.equals(toolCall.toolCode()))
                    .map(toolCall -> requiredText(toolCall.argumentsJson(), PREFLIGHT_DECISION_TOOL_CODE + ".argumentsJson"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Structured preflight decision tool call is required: " + PREFLIGHT_DECISION_TOOL_CODE
                    ));
        }
        return response.assistantText();
    }

    private AgentLlmToolSchema buildPreflightDecisionToolSchema() {
        try {
            LinkedHashMap<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");

            LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
            properties.put("behaviorType", Map.of(
                    "type", "string",
                    "enum", Arrays.stream(AgentBehaviorType.values()).map(Enum::name).toList()
            ));
            properties.put("executionPromptProfile", Map.of("type", "string"));
            properties.put("includeStyleContext", Map.of("type", "boolean"));
            properties.put("includeRagContext", Map.of("type", "boolean"));
            properties.put("includeStoryBibleContext", Map.of("type", "boolean"));
            properties.put("intentTags", Map.of(
                    "type", "array",
                    "items", Map.of(
                            "type", "string",
                            "enum", Arrays.stream(TaskIntentTag.values()).map(Enum::name).toList()
                    )
            ));
            properties.put("hardConstraints", Map.of(
                    "type", "array",
                    "items", Map.of("type", "string")
            ));
            properties.put("enabledSkills", Map.of(
                    "type", "array",
                    "items", Map.of("type", "string")
            ));
            properties.put("enabledTools", Map.of(
                    "type", "array",
                    "items", Map.of("type", "string")
            ));
            properties.put("outputExpectation", Map.of("type", List.of("string", "null")));
            properties.put("needsApproval", Map.of("type", "boolean"));
            properties.put("needsStoryBibleUpdate", Map.of("type", "boolean"));
            properties.put("needsClarification", Map.of("type", "boolean"));
            properties.put("reasoningSummary", Map.of("type", "string"));
            schema.put("properties", properties);
            schema.put("required", List.of(
                    "behaviorType",
                    "executionPromptProfile",
                    "includeStyleContext",
                    "includeRagContext",
                    "includeStoryBibleContext",
                    "intentTags",
                    "hardConstraints",
                    "enabledSkills",
                    "enabledTools",
                    "outputExpectation",
                    "needsApproval",
                    "needsStoryBibleUpdate",
                    "needsClarification",
                    "reasoningSummary"
            ));
            schema.put("additionalProperties", false);
            return new AgentLlmToolSchema(
                    PREFLIGHT_DECISION_TOOL_CODE,
                    PREFLIGHT_DECISION_TOOL_DESCRIPTION,
                    objectMapper.writeValueAsString(schema)
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build preflight decision schema", ex);
        }
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
            List<String> intentTags = normalizeIntentTags(requiredStringList(root, "intentTags"));
            List<String> hardConstraints = requiredStringList(root, "hardConstraints");
            List<String> enabledSkills = requiredStringList(root, "enabledSkills");
            List<String> enabledTools = requiredStringList(root, "enabledTools");
            String outputExpectation = requiredNullableStringField(root, "outputExpectation");
            boolean needsApproval = requiredBooleanField(root, "needsApproval");
            boolean needsStoryBibleUpdate = requiredBooleanField(root, "needsStoryBibleUpdate");
            boolean needsClarification = requiredBooleanField(root, "needsClarification");
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

    private List<String> requiredStringList(JsonNode root, String fieldName) {
        JsonNode field = root == null ? null : root.get(fieldName);
        if (field == null || field.isNull()) {
            throw new IllegalArgumentException(fieldName + " is required");
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

    private List<String> normalizeIntentTags(List<String> rawIntentTags) {
        if (rawIntentTags == null || rawIntentTags.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String rawIntentTag : rawIntentTags) {
            String candidate = rawIntentTag == null ? null : rawIntentTag.trim().toUpperCase();
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            if (isSupportedIntentTag(candidate)) {
                if (!normalized.contains(candidate)) {
                    normalized.add(candidate);
                }
                continue;
            }
            log.warn("Agent preflight returned unsupported intent tag, ignored: {}", rawIntentTag);
        }
        return List.copyOf(normalized);
    }

    private boolean isSupportedIntentTag(String candidate) {
        try {
            TaskIntentTag.valueOf(candidate);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String requiredNullableStringField(JsonNode root, String fieldName) {
        JsonNode field = root == null ? null : root.get(fieldName);
        if (field == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (field.isNull()) {
            return null;
        }
        if (!field.isTextual() || field.asText().isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be string or null");
        }
        return field.asText().trim();
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
