package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.agent.prompt.SkillCatalogItem;
import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import com.penmate.backend.application.common.serialization.JsonCodec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class SkillLoadToolDefinition implements AgentToolDefinition {

    private final SkillPromptRegistry skillPromptRegistry;
    private final JsonCodec jsonCodec;

    public SkillLoadToolDefinition(SkillPromptRegistry skillPromptRegistry, JsonCodec jsonCodec) {
        this.skillPromptRegistry = Objects.requireNonNull(skillPromptRegistry, "skillPromptRegistry");
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec");
    }

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "skill_load",
                new ToolPresentation("技能加载"),
                new ToolExposure(
                        ToolLifecycleStatus.ACTIVE,
                        "Load full skill instructions by skill name after reviewing the Available skills catalog.",
                        buildParametersJsonSchema()
                ),
                new ToolGovernancePolicy(
                        new ApprovalPolicyDecision(false, ""),
                        0,
                        Map.of()
                )
        );
    }

    private String buildParametersJsonSchema() {
        List<String> skillNames = skillPromptRegistry.listAvailableSkills().stream()
                .map(SkillCatalogItem::name)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .toList();

        Map<String, Object> skillProperty = new LinkedHashMap<>();
        skillProperty.put("type", "string");
        skillProperty.put("description", "Skill name from the Available skills catalog.");
        skillProperty.put("enum", skillNames);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("skill", skillProperty);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("skill"));
        schema.put("additionalProperties", false);
        return jsonCodec.write(schema);
    }
}
