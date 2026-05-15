package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * quality_review tool 的静态定义。
 */
@Component
public class QualityReviewToolDefinition implements AgentToolDefinition {

    private static final String PARAMETERS_JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "draftText": {
                  "type": "string"
                },
                "userRequirements": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                },
                "personaProfile": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                },
                "storyOutline": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                },
                "timelineConstraints": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                },
                "worldRules": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                },
                "characterKnowledgeBoundaries": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                },
                "currentRevisionRound": {
                  "type": "integer",
                  "minimum": 0
                },
                "maxRevisionRounds": {
                  "type": "integer",
                  "minimum": 0
                }
              },
              "required": ["draftText", "userRequirements", "personaProfile", "storyOutline", "timelineConstraints", "worldRules", "characterKnowledgeBoundaries", "currentRevisionRound", "maxRevisionRounds"],
              "additionalProperties": false
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "quality_review",
                new ToolPresentation("质量审查"),
                new ToolExposure(true, "审查正文质量并输出结构化质量报告与修订建议", PARAMETERS_JSON_SCHEMA),
                new ToolGovernancePolicy(
                        new ApprovalPolicyDecision(false, ""),
                        1,
                        Map.of()
                )
        );
    }
}
