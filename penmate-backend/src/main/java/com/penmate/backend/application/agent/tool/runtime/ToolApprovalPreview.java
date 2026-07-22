package com.penmate.backend.application.agent.tool.runtime;

import com.penmate.backend.application.common.serialization.JsonCodec;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

@Component
public class ToolApprovalPreview {
    private final JsonCodec jsonCodec;

    public ToolApprovalPreview(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public Map<String, String> from(String toolCode, String toolArgsJson) {
        try {
            Map<String, Object> args = jsonCodec.readObject(
                    toolArgsJson == null || toolArgsJson.isBlank() ? "{}" : toolArgsJson);
            LinkedHashMap<String, String> preview = new LinkedHashMap<>();
            putText(preview, "operation", args.get("operation"));
            if ("story_bible_update".equals(toolCode)) {
                Object rawOperations = args.get("operations");
                if (rawOperations instanceof List<?> operations && !operations.isEmpty()
                        && operations.getFirst() instanceof Map<?, ?> first) {
                    putText(preview, "kind", first.get("kind"));
                    Long nodeId = null;
                    for (Object rawOperation : operations) {
                        if (!(rawOperation instanceof Map<?, ?> operation)) continue;
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

    private static Long firstNodeId(Map<?, ?> operation) {
        for (String field : new String[]{"nodeId", "sourceNodeId", "targetNodeId"}) {
            Object value = operation.get(field);
            if (value instanceof Number number && number.longValue() > 0) return number.longValue();
        }
        return null;
    }

    private static void putText(Map<String, String> preview, String field, Object value) {
        if ((value instanceof String || value instanceof Number || value instanceof Boolean)
                && !String.valueOf(value).isBlank()) {
            preview.put(field, String.valueOf(value));
        }
    }
}
