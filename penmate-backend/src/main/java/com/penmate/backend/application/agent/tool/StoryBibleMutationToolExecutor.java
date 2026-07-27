package com.penmate.backend.application.agent.tool;

import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.common.serialization.JsonCodec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Adapts narrow, structured Story Bible write tools to the transactional mutation service. */
@Component
public class StoryBibleMutationToolExecutor {

    private final StoryBibleUpdateApplicationService updateService;
    private final JsonCodec jsonCodec;

    public StoryBibleMutationToolExecutor(StoryBibleUpdateApplicationService updateService, JsonCodec jsonCodec) {
        this.updateService = updateService;
        this.jsonCodec = jsonCodec;
    }

    public void validate(ToolCallRequest request, Map<String, OperationSpec> operations) {
        if (request == null) throw new IllegalArgumentException("request is required");
        if (request.toolArgsJson().length() > 100_000) {
            throw new IllegalArgumentException("serialized Story Bible batch exceeds 100000 characters");
        }
        Map<String, Object> arguments = jsonCodec.readObject(request.toolArgsJson());
        for (Map<String, Object> item : items(arguments)) {
            OperationSpec spec = requireOperation(item, operations);
            requireFields(item, spec.requiredFields());
        }
    }

    public ToolCallResult execute(AuthorizedAgentRunContext context, ToolCallRequest request,
                                  Map<String, OperationSpec> operations) {
        try {
            Map<String, Object> arguments = jsonCodec.readObject(request.toolArgsJson());
            List<StoryBibleUpdateApplicationService.MutationCommand> commands = new ArrayList<>();
            for (Map<String, Object> item : items(arguments)) {
                OperationSpec spec = requireOperation(item, operations);
                requireFields(item, spec.requiredFields());
                Map<String, Object> mutation = new LinkedHashMap<>(item);
                mutation.remove("operation");
                moveStructuredJson(mutation, "attributes", "attributesJson");
                moveStructuredJson(mutation, "patch", "patchJson");
                moveStructuredJson(mutation, "fieldSchema", "fieldSchemaJson");
                commands.add(new StoryBibleUpdateApplicationService.MutationCommand(spec.mutationKind(), mutation));
            }
            return updateService.executeBatch(context, commands);
        } catch (RuntimeException exception) {
            return ToolCallResult.failed("STORY_BIBLE_WRITE_FAILED", message(exception));
        }
    }

    private static List<Map<String, Object>> items(Map<String, Object> arguments) {
        Object raw = arguments.get("items");
        if (!(raw instanceof List<?> values)) {
            throw new IllegalArgumentException("items is required and must be an array");
        }
        if (values.isEmpty() || values.size() > 25) {
            throw new IllegalArgumentException("items must contain between 1 and 25 mutations");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Every batch item must be an object");
            Map<String, Object> item = new LinkedHashMap<>();
            map.forEach((key, itemValue) -> item.put(String.valueOf(key), itemValue));
            result.add(item);
        }
        return List.copyOf(result);
    }

    private static OperationSpec requireOperation(Map<String, Object> arguments,
                                                  Map<String, OperationSpec> operations) {
        String operation = requiredText(arguments, "operation");
        OperationSpec spec = operations.get(operation);
        if (spec == null) throw new IllegalArgumentException("unsupported operation: " + operation);
        return spec;
    }

    private void moveStructuredJson(Map<String, Object> mutation, String source, String target) {
        if (!mutation.containsKey(source)) return;
        Object value = mutation.remove(source);
        mutation.put(target, jsonCodec.write(value == null ? Map.of() : value));
    }

    private static void requireFields(Map<String, Object> arguments, Set<String> requiredFields) {
        List<String> missing = new ArrayList<>();
        for (String field : requiredFields) {
            Object value = arguments.get(field);
            if (value == null || value instanceof String text && text.isBlank()) missing.add(field);
        }
        if (!missing.isEmpty()) throw new IllegalArgumentException("missing required fields: " + String.join(", ", missing));
    }

    private static String requiredText(Map<String, Object> arguments, String field) {
        Object value = arguments.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return text.trim();
    }

    private static String message(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    public record OperationSpec(String mutationKind, Set<String> requiredFields) {
        public OperationSpec {
            requiredFields = Set.copyOf(requiredFields == null ? Set.of() : requiredFields);
        }

        public static OperationSpec of(String mutationKind, String... requiredFields) {
            return new OperationSpec(mutationKind, Set.of(requiredFields));
        }
    }
}
