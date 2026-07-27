package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Exposes current-session Todo CRUD without performing planning on the agent's behalf. */
@Component
public class TodoCrudToolDefinition implements AgentToolDefinition {

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
                "todo_crud",
                new ToolPresentation("Todo CRUD"),
                new ToolExposure(
                        ToolLifecycleStatus.DISABLED,
                        "List or maintain persisted Todos for the current session. The runtime supplies session scope. "
                                + "Create and update require title, sourceType, and todoStatus; update, complete, and "
                                + "delete require a todoId returned by list. Mutation results return the persisted Todo "
                                + "or deletion receipt with changed=true.",
                        PARAMETERS_JSON_SCHEMA
                ),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
        );
    }
}
