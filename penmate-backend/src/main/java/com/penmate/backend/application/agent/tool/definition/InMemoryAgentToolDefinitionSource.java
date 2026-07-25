package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于内存 registry 的 Agent tool definition source。
 * <p>该类不再自己维护每个 tool 的 schema 或展示文案，而是聚合多个 {@link AgentToolDefinition}，
 * 将它们声明的 descriptor 组装为查询索引与 LLM schema 列表。</p>
 */
@Component
@Slf4j
public class InMemoryAgentToolDefinitionSource implements AgentToolDefinitionSource {

    private final Map<String, AgentToolDescriptor> registry;
    private final List<AgentToolDescriptor> descriptors;

    public InMemoryAgentToolDefinitionSource(List<AgentToolDefinition> definitions) {
        RegistrySnapshot snapshot = buildRegistry(definitions);
        this.registry = snapshot.registry();
        this.descriptors = snapshot.descriptors();
    }

    private static RegistrySnapshot buildRegistry(List<AgentToolDefinition> definitions) {
        Map<String, AgentToolDescriptor> registry = new LinkedHashMap<>();
        for (AgentToolDefinition definition : definitions) {
            AgentToolDescriptor descriptor = requireValidDescriptor(definition.descriptor());
            AgentToolDescriptor duplicated = registry.putIfAbsent(descriptor.toolCode(), descriptor);
            if (duplicated != null) {
                throw new IllegalArgumentException("Duplicated tool definition: " + descriptor.toolCode());
            }
        }
        List<AgentToolDescriptor> descriptors = List.copyOf(registry.values());
        return new RegistrySnapshot(Map.copyOf(registry), descriptors);
    }

    private static AgentToolDescriptor requireValidDescriptor(AgentToolDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("tool descriptor must not be null");
        }
        if (descriptor.toolCode() == null || descriptor.toolCode().isBlank()) {
            throw new IllegalArgumentException("toolCode must not be blank");
        }
        if (descriptor.exposure() == null) {
            throw new IllegalArgumentException("exposure must not be null: " + descriptor.toolCode());
        }
        if (descriptor.governancePolicy() == null) {
            throw new IllegalArgumentException("governancePolicy must not be null: " + descriptor.toolCode());
        }
        return descriptor;
    }

    @Override
    public AgentToolDescriptor getRequired(String toolCode) {
        AgentToolDescriptor descriptor = registry.get(toolCode);
        if (descriptor == null) {
            log.warn("读取 tool descriptor 失败: toolCode={}, reason=not_found", toolCode);
            throw new IllegalArgumentException("Tool descriptor not found: " + toolCode);
        }
        return descriptor;
    }

    @Override
    public List<AgentToolDescriptor> listAll() {
        return descriptors;
    }

    @Override
    public List<AgentLlmToolSchema> listLlmSchemas() {
        return descriptors.stream()
                .filter(descriptor -> descriptor.exposure().lifecycleStatus().selectableForNewRuns())
                .map(descriptor -> new AgentLlmToolSchema(
                        descriptor.toolCode(),
                        descriptor.exposure().llmDescription(),
                        descriptor.exposure().parametersJsonSchema()
                ))
                .toList();
    }

    private record RegistrySnapshot(
            Map<String, AgentToolDescriptor> registry,
            List<AgentToolDescriptor> descriptors
    ) {
    }
}
