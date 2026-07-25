package com.penmate.backend.application.agent.tool.runtime;

import com.penmate.backend.application.common.serialization.JsonCodec;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ToolApprovalPreview {
    private final JsonCodec jsonCodec;
    private final Map<String, ToolApprovalPreviewProvider> providers;

    public ToolApprovalPreview(JsonCodec jsonCodec, List<ToolApprovalPreviewProvider> providers) {
        this.jsonCodec = jsonCodec;
        this.providers = (providers == null ? List.<ToolApprovalPreviewProvider>of() : providers).stream()
                .collect(Collectors.toUnmodifiableMap(
                        ToolApprovalPreviewProvider::toolCode,
                        Function.identity(),
                        (first, duplicate) -> {
                            throw new IllegalArgumentException(
                                    "Duplicated tool approval preview provider: " + first.toolCode());
                        }
                ));
    }

    public Map<String, String> from(String toolCode, String toolArgsJson) {
        try {
            Map<String, Object> args = jsonCodec.readObject(
                    toolArgsJson == null || toolArgsJson.isBlank() ? "{}" : toolArgsJson);
            LinkedHashMap<String, String> preview = new LinkedHashMap<>();
            putText(preview, "operation", args.get("operation"));
            ToolApprovalPreviewProvider provider = providers.get(toolCode);
            if (provider != null) preview.putAll(provider.preview(args));
            return Collections.unmodifiableMap(preview);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static void putText(Map<String, String> preview, String field, Object value) {
        if ((value instanceof String || value instanceof Number || value instanceof Boolean)
                && !String.valueOf(value).isBlank()) {
            preview.put(field, String.valueOf(value));
        }
    }
}
