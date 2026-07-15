package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DefaultStoryBibleSelectorGateway implements StoryBibleSelectorGateway {
    private static final String PROMPT_PATH = "prompts/agent/system/context-selector/00-selector-contract.md";

    private final AgentLlmGateway llmGateway;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;

    public DefaultStoryBibleSelectorGateway(AgentLlmGateway llmGateway, ObjectMapper objectMapper) {
        this.llmGateway = llmGateway;
        this.objectMapper = objectMapper;
        this.systemPrompt = loadPrompt();
    }

    @Override
    public Selection select(String userMessage, List<StoryBibleRouteRequest.CatalogEntry> catalog,
                            com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig executionConfig) {
        if (executionConfig == null) throw BusinessException.badRequest("Selector model configuration is required");
        Set<Long> allowed = catalog.stream().map(StoryBibleRouteRequest.CatalogEntry::nodeId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String catalogJson;
        try {
            catalogJson = objectMapper.writeValueAsString(catalog);
        } catch (JsonProcessingException ex) {
            throw BusinessException.of("Failed to serialize Story Bible selector catalog");
        }
        var response = llmGateway.generateTurn(new AgentLlmTurnRequest(
                List.of(AgentLlmMessage.system(systemPrompt), AgentLlmMessage.user(
                        "USER_REQUEST:\n" + (userMessage == null ? "" : userMessage) + "\n\nSELECTOR_CATALOG_JSON:\n" + catalogJson)),
                List.of(), "none"), executionConfig);
        return parse(response.assistantText(), allowed);
    }

    private Selection parse(String value, Set<Long> allowed) {
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
            Map<Long, String> reasons = new LinkedHashMap<>();
            JsonNode reasonNode = root.get("reasons");
            if (reasonNode != null && reasonNode.isObject()) {
                reasonNode.fields().forEachRemaining(entry -> {
                    try {
                        long id = Long.parseLong(entry.getKey());
                        if (allowed.contains(id) && entry.getValue().isTextual()) reasons.put(id, entry.getValue().asText());
                    } catch (NumberFormatException ignored) {
                        // Unknown reason keys do not affect the validated selection.
                    }
                });
            }
            return new Selection(ids, reasons);
        } catch (JsonProcessingException ex) {
            throw BusinessException.badRequest("Story Bible selector returned invalid JSON");
        }
    }

    private Long parseId(JsonNode node) {
        if (node.isTextual()) {
            try { return Long.valueOf(node.asText()); } catch (NumberFormatException ignored) { }
        }
        if (node.isIntegralNumber()) return node.longValue();
        throw BusinessException.badRequest("Story Bible selector node IDs must be strings");
    }

    private String loadPrompt() {
        try (var input = new ClassPathResource(PROMPT_PATH).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Missing Story Bible selector prompt", ex);
        }
    }
}
