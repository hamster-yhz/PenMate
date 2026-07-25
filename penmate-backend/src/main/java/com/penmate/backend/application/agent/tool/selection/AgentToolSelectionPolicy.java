package com.penmate.backend.application.agent.tool.selection;

import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Selects the immutable LLM tool set for one Agent Run. */
@Component
public class AgentToolSelectionPolicy {

    private final AgentToolDefinitionSource definitions;

    public AgentToolSelectionPolicy(AgentToolDefinitionSource definitions) {
        this.definitions = definitions;
    }

    public List<AgentLlmToolSchema> select(TaskProfile profile) {
        String executionProfile = profile == null ? "default" : profile.executionProfile();
        Map<String, AgentToolDescriptor> available = definitions.listAll().stream()
                .filter(descriptor -> descriptor.exposure().lifecycleStatus().selectableForNewRuns())
                .filter(descriptor -> descriptor.exposure().supportsProfile(executionProfile))
                .collect(Collectors.toMap(AgentToolDescriptor::toolCode, Function.identity()));

        List<String> requested = profile == null ? List.of() : normalizeRequested(profile.tools());
        if (!requested.isEmpty()) {
            for (String toolCode : requested) {
                if (!available.containsKey(toolCode)) {
                    throw new IllegalArgumentException(
                            "Task profile requests an unavailable tool: " + toolCode);
                }
            }
            return requested.stream().map(available::get).map(this::toSchema).toList();
        }
        return available.values().stream()
                .sorted(Comparator.comparing(AgentToolDescriptor::toolCode))
                .map(this::toSchema)
                .toList();
    }

    private List<String> normalizeRequested(List<String> requested) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String toolCode : requested == null ? List.<String>of() : requested) {
            if (toolCode != null && !toolCode.isBlank()) {
                normalized.add(toolCode.trim().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(normalized);
    }

    private AgentLlmToolSchema toSchema(AgentToolDescriptor descriptor) {
        return new AgentLlmToolSchema(
                descriptor.toolCode(),
                descriptor.exposure().llmDescription(),
                descriptor.exposure().parametersJsonSchema()
        );
    }
}
