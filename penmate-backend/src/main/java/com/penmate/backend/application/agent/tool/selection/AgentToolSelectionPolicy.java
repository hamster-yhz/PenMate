package com.penmate.backend.application.agent.tool.selection;

import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Selects the immutable LLM tool set for one Agent Run. */
@Component
public class AgentToolSelectionPolicy {

    private final AgentToolDefinitionSource definitions;

    public AgentToolSelectionPolicy(AgentToolDefinitionSource definitions) {
        this.definitions = definitions;
    }

    public List<AgentLlmToolSchema> select() {
        return definitions.listAll().stream()
                .filter(descriptor -> descriptor.exposure().lifecycleStatus().selectableForNewRuns())
                .sorted(Comparator.comparing(AgentToolDescriptor::toolCode))
                .map(this::toSchema)
                .toList();
    }

    private AgentLlmToolSchema toSchema(AgentToolDescriptor descriptor) {
        return new AgentLlmToolSchema(
                descriptor.toolCode(),
                descriptor.exposure().llmDescription(),
                descriptor.exposure().parametersJsonSchema()
        );
    }
}
