package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChapterEditToolDefinition implements AgentToolDefinition {

    private static final String PARAMETERS_JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "instruction": {
                  "type": "string",
                  "minLength": 1,
                  "pattern": ".*\\\\S.*"
                }
              },
              "required": ["instruction"],
              "additionalProperties": false
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "chapter_edit",
                new ToolPresentation("Edit chapter"),
                new ToolExposure(
                        ToolLifecycleStatus.ACTIVE,
                        "Edit the active chapter bound to the current Run",
                        PARAMETERS_JSON_SCHEMA,
                        java.util.Set.of("default", "rewrite")),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 2, Map.of()));
    }
}
