package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.llm.AgentLlmInvocationService;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DefaultStoryBibleSelectorGateway implements StoryBibleSelectorGateway {
    private static final String TOOL_CODE = "select_story_bible_context";
    private static final Duration SELECTOR_TIMEOUT = Duration.ofSeconds(10);
    private static final AgentLlmToolSchema OUTPUT_TOOL = new AgentLlmToolSchema(
            TOOL_CODE,
            "Return the validated Story Bible context selection",
            """
                    {
                      "type": "object",
                      "additionalProperties": false,
                      "required": ["intentTags", "selectedNodeIds", "relationExpansion", "selectionReasons", "missingContextFlags", "confidence"],
                      "properties": {
                        "intentTags": { "type": "array", "items": { "type": "string" } },
                        "selectedNodeIds": { "type": "array", "items": { "type": "string" } },
                        "relationExpansion": { "type": "array", "items": { "type": "string" } },
                        "selectionReasons": { "type": "object", "additionalProperties": { "type": "string" } },
                        "missingContextFlags": { "type": "array", "items": { "type": "string" } },
                        "confidence": { "type": "number", "minimum": 0, "maximum": 1 }
                      }
                    }
                    """);

    private final AgentLlmInvocationService llmInvocations;
    private final JsonCodec jsonCodec;
    private final String systemPrompt;

    public DefaultStoryBibleSelectorGateway(AgentLlmInvocationService llmInvocations, JsonCodec jsonCodec,
                                            SystemPromptProvider promptProvider) {
        this.llmInvocations = llmInvocations;
        this.jsonCodec = jsonCodec;
        this.systemPrompt = promptProvider.loadBundle("context-selector", "default").assembledPrompt();
    }

    @Override
    public Selection select(SelectorRequest request,
                            com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig executionConfig) {
        if (executionConfig == null) throw BusinessException.badRequest("Selector model configuration is required");
        List<StoryBibleRouteRequest.CatalogEntry> catalog = request.catalog();
        Set<Long> allowed = catalog.stream().map(StoryBibleRouteRequest.CatalogEntry::nodeId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String catalogJson = json(catalog);
        boolean stableCatalog = request.mode() == StoryBibleRoutingMode.LLM_SELECTOR;
        String stableSystem = stableCatalog
                ? systemPrompt + "\n\nSELECTOR_CATALOG_JSON:\n" + catalogJson
                : systemPrompt;
        StringBuilder dynamic = new StringBuilder();
        if (!stableCatalog) dynamic.append("RETRIEVAL_CANDIDATES_JSON:\n").append(catalogJson).append("\n\n");
        dynamic.append("CONVERSATION_WINDOW_JSON:\n").append(json(request.conversationWindow())).append("\n\n");
        dynamic.append("WORKING_SET_NODE_IDS:\n").append(json(request.workingSetNodeIds())).append("\n\n")
                .append("USER_REQUEST:\n").append(request.userMessage());
        var response = llmInvocations.invokeBuffered(new AgentLlmTurnRequest(
                List.of(AgentLlmMessage.system(stableSystem), AgentLlmMessage.user(dynamic.toString())),
                List.of(OUTPUT_TOOL), "required", SELECTOR_TIMEOUT), executionConfig);
        List<AgentLlmToolCall> calls = response.toolCalls().stream()
                .filter(item -> TOOL_CODE.equals(item.toolCode())).toList();
        if (response.toolCalls().size() != 1 || calls.size() != 1) {
            throw BusinessException.badRequest(
                    "Story Bible selector did not return exactly one required structured output");
        }
        return parse(calls.getFirst().argumentsJson(), catalog, allowed, response.tokenUsage());
    }

    private Selection parse(String value, List<StoryBibleRouteRequest.CatalogEntry> catalog, Set<Long> allowed,
                            com.penmate.backend.domain.agent.run.model.LlmTokenUsage tokenUsage) {
        Map<String, Object> root;
        try {
            root = jsonCodec.readObject(value);
        } catch (RuntimeException exception) {
            throw BusinessException.badRequest("Story Bible selector returned invalid JSON");
        }
        Object selectedValue = root.get("selectedNodeIds");
        if (!(selectedValue instanceof List<?> selectedValues)) {
            throw BusinessException.badRequest("Story Bible selector returned invalid structured output");
        }
        List<Long> selectedIds = ids(selectedValues, "selectedNodeIds", allowed);
        List<Long> relationExpansion = ids(root.get("relationExpansion"), "relationExpansion", allowed);
        Set<Long> adjacent = new LinkedHashSet<>();
        catalog.stream().filter(entry -> selectedIds.contains(entry.nodeId())).forEach(entry ->
                entry.keyRelations().forEach(relation -> adjacent.add(relation.otherNodeId())));
        if (!adjacent.containsAll(relationExpansion)) {
            throw BusinessException.badRequest(
                    "Story Bible selector returned a non-adjacent relation expansion ID");
        }
        Map<Long, String> reasons = reasons(root.get("selectionReasons"), selectedIds);
        List<String> intentTags = strings(root.get("intentTags"), "intentTags");
        List<String> missingFlags = strings(root.get("missingContextFlags"), "missingContextFlags");
        Object confidenceValue = root.get("confidence");
        if (!(confidenceValue instanceof Number confidence)
                || confidence.doubleValue() < 0d || confidence.doubleValue() > 1d) {
            throw BusinessException.badRequest("Story Bible selector confidence must be between 0 and 1");
        }
        return new Selection(intentTags, selectedIds, relationExpansion, reasons, missingFlags,
                confidence.doubleValue(), tokenUsage);
    }

    private List<Long> ids(Object value, String field, Set<Long> allowed) {
        if (!(value instanceof List<?> values)) {
            throw BusinessException.badRequest("Story Bible selector returned invalid " + field);
        }
        List<Long> result = new ArrayList<>();
        for (Object item : values) {
            Long id = parseId(item);
            if (!allowed.contains(id)) {
                throw BusinessException.badRequest("Story Bible selector returned an unknown node ID");
            }
            if (!result.contains(id)) result.add(id);
        }
        return List.copyOf(result);
    }

    private List<String> strings(Object value, String field) {
        if (!(value instanceof List<?> values)) {
            throw BusinessException.badRequest("Story Bible selector returned invalid " + field);
        }
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof String string)) {
                throw BusinessException.badRequest(
                        "Story Bible selector " + field + " must contain strings");
            }
            String normalized = string.trim();
            if (!normalized.isEmpty() && !result.contains(normalized)) result.add(normalized);
        }
        return List.copyOf(result);
    }

    private Map<Long, String> reasons(Object value, List<Long> selectedIds) {
        if (!(value instanceof Map<?, ?> values)) {
            throw BusinessException.badRequest("Story Bible selector returned invalid selectionReasons");
        }
        Map<Long, String> reasons = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            long reasonId;
            try {
                reasonId = Long.parseLong(String.valueOf(entry.getKey()));
            } catch (NumberFormatException exception) {
                throw BusinessException.badRequest(
                        "Story Bible selector returned an invalid selection reason ID");
            }
            if (!selectedIds.contains(reasonId) || !(entry.getValue() instanceof String reason)) {
                throw BusinessException.badRequest(
                        "Story Bible selector reasons must reference selected node IDs");
            }
            reasons.put(reasonId, reason.trim());
        }
        if (!reasons.keySet().containsAll(selectedIds)) {
            throw BusinessException.badRequest("Story Bible selector must explain every selected node ID");
        }
        return Map.copyOf(reasons);
    }

    private Long parseId(Object value) {
        if (value instanceof String string) {
            try {
                return Long.valueOf(string);
            } catch (NumberFormatException ignored) {
                // Continue to the stable validation error.
            }
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        throw BusinessException.badRequest("Story Bible selector node IDs must be strings");
    }

    private String json(Object value) {
        try {
            return jsonCodec.write(value);
        } catch (RuntimeException exception) {
            throw BusinessException.of("Failed to serialize Story Bible selector input");
        }
    }
}
