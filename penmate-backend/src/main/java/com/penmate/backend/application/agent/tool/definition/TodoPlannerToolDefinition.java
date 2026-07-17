package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * todo_planner tool 的静态定义。
 */
@Component
public class TodoPlannerToolDefinition implements AgentToolDefinition {

    private static final String PARAMETERS_JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "planningMode": {
                  "type": "string",
                  "enum": ["TASK_BREAKDOWN", "QUALITY_REMEDIATION", "FOLLOW_UP_MODIFICATION"],
                  "description": "TASK_BREAKDOWN 需要 userRequest；QUALITY_REMEDIATION 需要 qualityIssues；FOLLOW_UP_MODIFICATION 至少应提供 userRequest、qualityIssues、storyBibleUpdates、planningContext、existingTodos 之一"
                },
                "userRequest": {
                  "type": "string"
                },
                "qualityIssues": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "severity": {
                        "type": "string"
                      },
                      "summary": {
                        "type": "string"
                      },
                      "suggestion": {
                        "type": "string"
                      }
                    },
                    "required": ["severity", "summary", "suggestion"],
                    "additionalProperties": false
                  }
                },
                "storyBibleUpdates": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                },
                "planningContext": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                },
                "existingTodos": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                }
              },
              "required": ["planningMode"],
              "oneOf": [
                {
                  "properties": {
                    "planningMode": {
                      "const": "TASK_BREAKDOWN"
                    }
                  },
                  "required": ["planningMode", "userRequest"]
                },
                {
                  "properties": {
                    "planningMode": {
                      "const": "QUALITY_REMEDIATION"
                    }
                  },
                  "required": ["planningMode", "qualityIssues"]
                },
                {
                  "properties": {
                    "planningMode": {
                      "const": "FOLLOW_UP_MODIFICATION"
                    }
                  },
                  "anyOf": [
                    {
                      "required": ["userRequest"]
                    },
                    {
                      "required": ["qualityIssues"]
                    },
                    {
                      "required": ["storyBibleUpdates"]
                    },
                    {
                      "required": ["planningContext"]
                    },
                    {
                      "required": ["existingTodos"]
                    }
                  ]
                }
              ],
              "additionalProperties": false
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "todo_planner",
                new ToolPresentation("Todo 规划"),
                new ToolExposure(true, "将用户请求、质量问题与后续规划整理为结构化 Todo 规划建议", PARAMETERS_JSON_SCHEMA),
                new ToolGovernancePolicy(
                        new ApprovalPolicyDecision(false, ""),
                        1,
                        Map.of()
                )
        );
    }
}
