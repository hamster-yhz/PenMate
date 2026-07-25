package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Static definition for direct manipulation of the current session's todo plan. */
@Component
public class TodoPlannerToolDefinition implements AgentToolDefinition {

    private static final String PARAMETERS_JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "operation": {
                  "type": "string",
                  "enum": ["list", "create", "update", "complete", "delete"]
                },
                "todoId": { "type": "integer", "minimum": 1 },
                "title": { "type": "string", "minLength": 1, "pattern": ".*\\\\S.*" },
                "description": { "type": "string" },
                "sourceType": {
                  "type": "string",
                  "enum": ["USER_REQUEST", "QUALITY_REVIEW", "STORY_BIBLE_UPDATE", "PLANNING"]
                },
                "todoStatus": {
                  "type": "string",
                  "enum": ["TODO", "IN_PROGRESS", "BLOCKED", "DONE"]
                }
              },
              "required": ["operation"],
              "additionalProperties": false
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "todo_planner",
                new ToolPresentation("任务计划"),
                new ToolExposure(
                        ToolLifecycleStatus.ACTIVE,
                        "读取或直接维护当前会话的任务计划。create/update 需要 title、sourceType 和 todoStatus；complete/delete 需要 todoId。",
                        PARAMETERS_JSON_SCHEMA
                ),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
        );
    }
}
