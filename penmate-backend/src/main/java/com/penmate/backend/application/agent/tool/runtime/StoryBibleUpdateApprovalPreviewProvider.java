package com.penmate.backend.application.agent.tool.runtime;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StoryBibleUpdateApprovalPreviewProvider implements ToolApprovalPreviewProvider {

    @Override
    public String toolCode() {
        return "story_bible_update";
    }

    @Override
    public Map<String, String> preview(Map<String, Object> toolArguments) {
        Object rawOperations = toolArguments.get("operations");
        if (!(rawOperations instanceof List<?> operations) || operations.isEmpty()
                || !(operations.getFirst() instanceof Map<?, ?> first)) {
            return Map.of();
        }
        LinkedHashMap<String, String> preview = new LinkedHashMap<>();
        putText(preview, "kind", first.get("kind"));
        Long nodeId = null;
        for (Object rawOperation : operations) {
            if (!(rawOperation instanceof Map<?, ?> operation)) continue;
            nodeId = firstNodeId(operation);
            if (nodeId != null) break;
        }
        if (nodeId != null) preview.put("nodeId", String.valueOf(nodeId));
        preview.put("operationCount", String.valueOf(operations.size()));
        return Map.copyOf(preview);
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
