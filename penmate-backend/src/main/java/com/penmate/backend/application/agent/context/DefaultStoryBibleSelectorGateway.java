package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.llm.AgentLlmInvocationService;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.time.Duration;
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
    private final ObjectMapper objectMapper;
    private final String systemPrompt;

    public DefaultStoryBibleSelectorGateway(AgentLlmInvocationService llmInvocations, ObjectMapper objectMapper,
                                            SystemPromptProvider promptProvider) {
        this.llmInvocations = llmInvocations;
        this.objectMapper = objectMapper;
        this.systemPrompt = promptProvider.loadBundle("context-selector", "default").assembledPrompt();
    }

    @Override
    public Selection select(SelectorRequest request,
                            com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig executionConfig) {
        if (executionConfig == null) throw BusinessException.badRequest("Selector model configuration is required");
        List<StoryBibleRouteRequest.CatalogEntry> catalog = request.catalog();
        Set<Long> allowed = catalog.stream().map(StoryBibleRouteRequest.CatalogEntry::nodeId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String catalogJson;
        try {
            catalogJson = objectMapper.writeValueAsString(catalog);
        } catch (JsonProcessingException ex) {
            throw BusinessException.of("Failed to serialize Story Bible selector catalog");
        }
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
            throw BusinessException.badRequest("Story Bible selector did not return exactly one required structured output");
        }
        AgentLlmToolCall call = calls.getFirst();
        return parse(call.argumentsJson(), catalog, allowed, response.tokenUsage());
    }

    private Selection parse(String value, List<StoryBibleRouteRequest.CatalogEntry> catalog, Set<Long> allowed,
                            com.penmate.backend.domain.agent.run.model.LlmTokenUsage tokenUsage) {
        try {
            JsonNode root = objectMapper.readTree(value);
            JsonNode selected = root == null ? null : root.get("selectedNodeIds");
            if (selected == null || !selected.isArray()) {
                throw BusinessException.badRequest("Story Bible selector returned invalid structured output");
            }
            List<Long> ids = new ArrayList<>();
            for (JsonNode item : selected) {
                Long id = parseId(item);
                if (!allowed.contains(id)) throw BusinessException.badRequest("Story Bible selector returned an unknown node ID");
                if (!ids.contains(id)) ids.add(id);
            }
            List<Long> relationExpansion = ids(root.get("relationExpansion"), "relationExpansion", allowed);
            Set<Long> adjacent = new LinkedHashSet<>();
            catalog.stream().filter(entry -> ids.contains(entry.nodeId())).forEach(entry ->
                    entry.keyRelations().forEach(relation -> adjacent.add(relation.otherNodeId())));
            if (!adjacent.containsAll(relationExpansion)) {
                throw BusinessException.badRequest("Story Bible selector returned a non-adjacent relation expansion ID");
            }
            Map<Long, String> reasons = new LinkedHashMap<>();
            JsonNode reasonNode = root.get("selectionReasons");
            if (reasonNode == null || !reasonNode.isObject()) {
                throw BusinessException.badRequest("Story Bible selector returned invalid selectionReasons");
            }
            var fields = reasonNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                long reasonId;
                try {
                    reasonId = Long.parseLong(entry.getKey());
                } catch (NumberFormatException ex) {
                    throw BusinessException.badRequest("Story Bible selector returned an invalid selection reason ID");
                }
                if (!ids.contains(reasonId) || !entry.getValue().isTextual()) {
                    throw BusinessException.badRequest("Story Bible selector reasons must reference selected node IDs");
                }
                reasons.put(reasonId, entry.getValue().asText().trim());
            }
            if (!reasons.keySet().containsAll(ids)) {
                throw BusinessException.badRequest("Story Bible selector must explain every selected node ID");
            }
            List<String> intentTags = strings(root.get("intentTags"), "intentTags");
            List<String> missingFlags = strings(root.get("missingContextFlags"), "missingContextFlags");
            JsonNode confidenceNode = root.get("confidence");
            if (confidenceNode == null || !confidenceNode.isNumber()
                    || confidenceNode.asDouble() < 0d || confidenceNode.asDouble() > 1d) {
                throw BusinessException.badRequest("Story Bible selector confidence must be between 0 and 1");
            }
            return new Selection(intentTags, ids, relationExpansion, reasons, missingFlags,
                    confidenceNode.asDouble(), tokenUsage);
        } catch (JsonProcessingException ex) {
            throw BusinessException.badRequest("Story Bible selector returned invalid JSON");
        }
    }

    private List<Long> ids(JsonNode node, String field, Set<Long> allowed) {
        if (node == null || !node.isArray()) {
            throw BusinessException.badRequest("Story Bible selector returned invalid " + field);
        }
        List<Long> result = new ArrayList<>();
        for (JsonNode item : node) {
            Long id = parseId(item);
            if (!allowed.contains(id)) throw BusinessException.badRequest("Story Bible selector returned an unknown node ID");
            if (!result.contains(id)) result.add(id);
        }
        return List.copyOf(result);
    }

    private List<String> strings(JsonNode node, String field) {
        if (node == null || !node.isArray()) {
            throw BusinessException.badRequest("Story Bible selector returned invalid " + field);
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) throw BusinessException.badRequest("Story Bible selector " + field + " must contain strings");
            String value = item.asText().trim();
            if (!value.isEmpty() && !result.contains(value)) result.add(value);
        }
        return List.copyOf(result);
    }

    private Long parseId(JsonNode node) {
        if (node.isTextual()) {
            try { return Long.valueOf(node.asText()); } catch (NumberFormatException ignored) { }
        }
        if (node.isIntegralNumber()) return node.longValue();
        throw BusinessException.badRequest("Story Bible selector node IDs must be strings");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw BusinessException.of("Failed to serialize Story Bible selector input");
        }
    }
}
