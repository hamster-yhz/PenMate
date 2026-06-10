package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TodoCrudToolDefinition implements AgentToolDefinition {

    private static final String PARAMETERS_JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "operation": {
                  "type": "string",
                  "enum": ["list", "get", "create", "update", "complete", "reorder", "delete"],
                  "description": "Agent session Todo runtime state operation"
                },
                "sessionId": {
                  "type": "integer",
                  "minimum": 1
                },
                "todoId": {
                  "type": "integer",
                  "minimum": 1
                },
                "taskId": {
                  "type": "integer",
                  "minimum": 1
                },
                "title": {
                  "type": "string",
                  "minLength": 1,
                  "pattern": ".*\\\\S.*"
                },
                "description": {
                  "type": "string"
                },
                "status": {
                  "type": "string",
                  "enum": ["pending", "in_progress", "completed", "blocked", "failed", "cancelled"]
                },
                "todoStatus": {
                  "type": "string",
                  "description": "Legacy alias for status"
                },
                "priority": {
                  "type": "integer"
                },
                "orderIndex": {
                  "type": "integer"
                },
                "orderedTodoIds": {
                  "type": "array",
                  "items": {
                    "type": "integer",
                    "minimum": 1
                  }
                },
                "dependencies": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                },
                "summary": {
                  "type": "string"
                },
                "blockedReason": {
                  "type": "string"
                },
                "errorSummary": {
                  "type": "string"
                },
                "metadata": {
                  "type": "string"
                }
              },
              "required": ["operation", "sessionId"],
              "additionalProperties": false
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "todo_crud",
                new ToolPresentation("Todo CRUD"),
                new ToolExposure(true, "List, get, create, update, reorder, complete, or delete Redis-backed session Todo tasks", PARAMETERS_JSON_SCHEMA),
                new ToolGovernancePolicy(
                        new ApprovalPolicyDecision(false, ""),
                        1,
                        Map.of()
                )
        );
    }
}
