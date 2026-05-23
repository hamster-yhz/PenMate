package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * todo_crud tool 的静态定义。
 */
@Component
public class TodoCrudToolDefinition implements AgentToolDefinition {

    private static final String PARAMETERS_JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "operation": {
                  "type": "string",
                  "enum": ["list", "create", "update", "complete", "delete"],
                  "description": "list 可选 todoStatus；create 需要 title/sourceType/todoStatus；update 需要 todoId/title/sourceType/todoStatus；complete/delete 需要 todoId"
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
                "sourceType": {
                  "type": "string"
                },
                "todoStatus": {
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
                new ToolPresentation("待办 CRUD"),
                new ToolExposure(true, "列出、创建、更新、完成或删除当前会话待办事项", PARAMETERS_JSON_SCHEMA),
                new ToolGovernancePolicy(
                        new ApprovalPolicyDecision(false, ""),
                        1,
                        Map.of()
                )
        );
    }
}
