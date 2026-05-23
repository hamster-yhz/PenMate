package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * draft_generation tool 的静态定义。
 */
@Component
public class DraftGenerationToolDefinition implements AgentToolDefinition {

    private static final String PARAMETERS_JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "operation": {
                  "type": "string",
                  "enum": ["generate", "rewrite", "revise"],
                  "description": "generate 需要 prompt；rewrite/revise 需要 sourceText 和 instruction"
                },
                "prompt": {
                  "type": "string"
                },
                "sourceText": {
                  "type": "string"
                },
                "instruction": {
                  "type": "string"
                },
                "preservedConstraints": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                },
                "sourceSummary": {
                  "type": "string"
                }
              },
              "required": ["operation"],
              "oneOf": [
                {
                  "properties": {
                    "operation": {
                      "const": "generate"
                    }
                  },
                  "required": ["operation", "prompt"]
                },
                {
                  "properties": {
                    "operation": {
                      "const": "rewrite"
                    }
                  },
                  "required": ["operation", "sourceText", "instruction"]
                },
                {
                  "properties": {
                    "operation": {
                      "const": "revise"
                    }
                  },
                  "required": ["operation", "sourceText", "instruction"]
                }
              ],
              "additionalProperties": false
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "draft_generation",
                new ToolPresentation("正文生成"),
                new ToolExposure(true, "生成正文、改写正文或套用修订", PARAMETERS_JSON_SCHEMA),
                new ToolGovernancePolicy(
                        new ApprovalPolicyDecision(false, ""),
                        1,
                        Map.of(
                                "generate", new ToolOperationPolicy("generate", new ApprovalPolicyDecision(false, "")),
                                "rewrite", new ToolOperationPolicy("rewrite", new ApprovalPolicyDecision(false, "")),
                                "revise", new ToolOperationPolicy("revise", new ApprovalPolicyDecision(false, ""))
                        )
                )
        );
    }
}
