package com.penmate.backend.application.agent.tool.runtime;

import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.handler.AgentToolHandler;
import com.penmate.backend.application.agent.tool.validation.AgentToolSchemaValidator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime registry that verifies every tool definition has exactly one executable handler.
 */
@Component
public class AgentToolRegistry {

    private final AgentToolSchemaValidator schemaValidator;
    private final Map<String, AgentToolDescriptor> descriptors;
    private final Map<String, AgentToolHandler> handlers;

    public AgentToolRegistry(AgentToolDefinitionSource definitions,
                             List<AgentToolHandler> handlers,
                             AgentToolSchemaValidator schemaValidator) {
        this.schemaValidator = schemaValidator;
        this.handlers = buildHandlers(handlers);
        this.descriptors = buildDescriptors(definitions);
    }

    public AgentToolDescriptor getRequiredDescriptor(String toolCode) {
        AgentToolDescriptor descriptor = descriptors.get(toolCode);
        if (descriptor == null) {
            throw new IllegalArgumentException("Tool descriptor not found: " + toolCode);
        }
        return descriptor;
    }

    public AgentToolHandler getRequiredHandler(String toolCode) {
        AgentToolHandler handler = handlers.get(toolCode);
        if (handler == null) {
            throw new IllegalArgumentException("Tool handler not found: " + toolCode);
        }
        return handler;
    }

    public void validateArguments(String toolCode, String toolArgsJson) {
        schemaValidator.validate(toolCode, toolArgsJson);
    }

    private Map<String, AgentToolHandler> buildHandlers(List<AgentToolHandler> candidates) {
        Map<String, AgentToolHandler> result = new LinkedHashMap<>();
        for (AgentToolHandler handler : candidates == null ? List.<AgentToolHandler>of() : candidates) {
            if (handler == null || handler.toolCode() == null || handler.toolCode().isBlank()) {
                throw new IllegalArgumentException("Tool handler code must not be blank");
            }
            AgentToolHandler duplicate = result.putIfAbsent(handler.toolCode(), handler);
            if (duplicate != null) {
                throw new IllegalArgumentException("Duplicated tool handler: " + handler.toolCode());
            }
        }
        return Map.copyOf(result);
    }

    private Map<String, AgentToolDescriptor> buildDescriptors(AgentToolDefinitionSource definitions) {
        Map<String, AgentToolDescriptor> result = new LinkedHashMap<>();
        for (AgentToolDescriptor descriptor : definitions.listAll()) {
            AgentToolDescriptor duplicate = result.putIfAbsent(descriptor.toolCode(), descriptor);
            if (duplicate != null) {
                throw new IllegalArgumentException("Duplicated tool definition: " + descriptor.toolCode());
            }
            if (!handlers.containsKey(descriptor.toolCode())) {
                throw new IllegalArgumentException("Tool definition has no handler: " + descriptor.toolCode());
            }
            schemaValidator.register(descriptor.toolCode(), descriptor.exposure().parametersJsonSchema());
        }
        for (String toolCode : handlers.keySet()) {
            if (!result.containsKey(toolCode)) {
                throw new IllegalArgumentException("Tool handler has no definition: " + toolCode);
            }
        }
        return Map.copyOf(result);
    }
}
