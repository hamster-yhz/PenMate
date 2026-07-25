package com.penmate.backend.infrastructure.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.penmate.backend.application.agent.tool.validation.AgentToolSchemaValidator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class NetworkntAgentToolSchemaValidator implements AgentToolSchemaValidator {

    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;
    private final Map<String, CompiledSchema> schemas = new LinkedHashMap<>();

    public NetworkntAgentToolSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    }

    @Override
    public synchronized void register(String toolCode, String parametersJsonSchema) {
        if (toolCode == null || toolCode.isBlank()) {
            throw new IllegalArgumentException("toolCode must not be blank");
        }
        if (parametersJsonSchema == null || parametersJsonSchema.isBlank()) {
            throw new IllegalArgumentException("Tool parameters schema must not be blank: " + toolCode);
        }
        try {
            JsonNode schemaNode = objectMapper.readTree(parametersJsonSchema);
            if (schemaNode == null || !schemaNode.isObject()) {
                throw new IllegalArgumentException("Tool parameters schema must be a JSON object: " + toolCode);
            }
            JsonSchema compiled = schemaFactory.getSchema(schemaNode);
            CompiledSchema previous = schemas.putIfAbsent(
                    toolCode, new CompiledSchema(parametersJsonSchema, compiled));
            if (previous != null && !previous.source().equals(parametersJsonSchema)) {
                throw new IllegalArgumentException("Conflicting tool parameters schema: " + toolCode);
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid tool parameters schema: " + toolCode, exception);
        }
    }

    @Override
    public void validate(String toolCode, String toolArgsJson) {
        CompiledSchema compiled = schemas.get(toolCode);
        if (compiled == null) {
            throw new IllegalArgumentException("Tool parameters schema is not registered: " + toolCode);
        }
        JsonNode arguments;
        try {
            arguments = objectMapper.readTree(toolArgsJson == null || toolArgsJson.isBlank() ? "{}" : toolArgsJson);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Tool arguments must be valid JSON", exception);
        }
        if (arguments == null || !arguments.isObject()) {
            throw new IllegalArgumentException("Tool arguments must be a JSON object");
        }
        Set<ValidationMessage> errors = compiled.schema().validate(arguments);
        if (!errors.isEmpty()) {
            String detail = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException("Tool arguments do not match schema: " + detail);
        }
    }

    private record CompiledSchema(String source, JsonSchema schema) {
    }
}
