package com.penmate.backend.application.agent.tool.validation;

/**
 * Compiles tool parameter schemas and validates concrete LLM tool arguments against them.
 */
public interface AgentToolSchemaValidator {

    void register(String toolCode, String parametersJsonSchema);

    void validate(String toolCode, String toolArgsJson);
}
