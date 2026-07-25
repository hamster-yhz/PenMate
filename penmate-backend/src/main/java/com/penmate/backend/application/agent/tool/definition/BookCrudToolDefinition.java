package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BookCrudToolDefinition implements AgentToolDefinition {

    private static final String PARAMETERS_JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "operation": { "type": "string", "enum": ["get", "update", "delete"] },
                "title": { "type": "string" },
                "summary": { "type": "string" },
                "status": { "type": "integer" }
              },
              "required": ["operation"],
              "additionalProperties": false
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "book_crud",
                new ToolPresentation("Book CRUD"),
                new ToolExposure(ToolLifecycleStatus.DISABLED,
                        "Read or update the current Run project", PARAMETERS_JSON_SCHEMA),
                new ToolGovernancePolicy(
                        new ApprovalPolicyDecision(false, ""),
                        2,
                        Map.of("delete", new ToolOperationPolicy(
                                "delete", new ApprovalPolicyDecision(true, "BOOK_DELETE")))));
    }
}
