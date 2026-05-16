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
                "chapterId": {
                  "type": "integer",
                  "minimum": 1
                },
                "draftId": {
                  "type": "integer",
                  "minimum": 1
                },
                "draftText": {
                  "type": "string",
                  "minLength": 1,
                  "pattern": ".*\\S.*"
                },
                "userRequirements": {
                  "type": "array",
                  "minItems": 1,
                  "items": {
                    "type": "string",
                    "pattern": ".*\\S.*"
                  }
                },
                "personaProfile": {
                  "type": "array",
                  "minItems": 1,
                  "items": {
                    "type": "string",
                    "pattern": ".*\\S.*"
                  }
                },
                "storyOutline": {
                  "type": "array",
                  "minItems": 1,
                  "items": {
                    "type": "string",
                    "pattern": ".*\\S.*"
                  }
                },
                "timelineConstraints": {
                  "type": "array",
                  "minItems": 1,
                  "items": {
                    "type": "string",
                    "pattern": ".*\\S.*"
                  }
                },
                "worldRules": {
                  "type": "array",
                  "minItems": 1,
                  "items": {
                    "type": "string",
                    "pattern": ".*\\S.*"
                  }
                },
                "characterKnowledgeBoundaries": {
                  "type": "array",
                  "minItems": 1,
                  "items": {
                    "type": "string",
                    "pattern": ".*\\S.*"
                  }
                },
                "currentRevisionRound": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "must be less than or equal to maxRevisionRounds"
                },
                "maxRevisionRounds": {
                  "type": "integer",
                  "minimum": 0
                }
              },
              "required": ["currentRevisionRound", "maxRevisionRounds"],
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
