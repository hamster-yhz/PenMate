package com.penmate.backend.application.agent.tool.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolApprovalPreview {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ToolApprovalPreview() {
    }

    public static Map<String, String> from(String toolCode, String toolArgsJson) {
        try {
            JsonNode args = OBJECT_MAPPER.readTree(toolArgsJson == null || toolArgsJson.isBlank() ? "{}" : toolArgsJson);
            LinkedHashMap<String, String> preview = new LinkedHashMap<>();
            putText(preview, "operation", args.get("operation"));
            if ("story_bible_update".equals(toolCode)) {
                JsonNode operations = args.get("operations");
                if (operations != null && operations.isArray() && !operations.isEmpty()) {
                    JsonNode first = operations.get(0);
                    putText(preview, "kind", first.get("kind"));
                    Long nodeId = null;
                    for (JsonNode operation : operations) {
                        nodeId = firstNodeId(operation);
                        if (nodeId != null) break;
                    }
                    if (nodeId != null) preview.put("nodeId", String.valueOf(nodeId));
                    preview.put("operationCount", String.valueOf(operations.size()));
                }
            }
            return Collections.unmodifiableMap(preview);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static Long firstNodeId(JsonNode operation) {
        for (String field : new String[]{"nodeId", "sourceNodeId", "targetNodeId"}) {
            JsonNode value = operation.get(field);
            if (value != null && value.canConvertToLong() && value.asLong() > 0) return value.asLong();
        }
        return null;
    }

    private static void putText(Map<String, String> preview, String field, JsonNode value) {
        if (value != null && value.isValueNode() && !value.asText().isBlank()) {
            preview.put(field, value.asText());
        }
    }
}
