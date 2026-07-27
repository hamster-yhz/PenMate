package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LedgerCrudToolDefinition implements AgentToolDefinition {
    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "operation": { "type": "string", "enum": ["list", "read", "create", "update", "delete"] },
                "ledgerId": { "type": "integer", "minimum": 1 },
                "title": { "type": "string", "minLength": 1, "maxLength": 120 },
                "content": { "type": "string", "maxLength": 20000 },
                "expectedRevision": { "type": "integer", "minimum": 1 },
                "offset": { "type": "integer", "minimum": 0 },
                "limit": { "type": "integer", "minimum": 1, "maximum": 20000 },
                "start": { "type": "integer", "minimum": 0 },
                "end": { "type": "integer", "minimum": 0 },
                "replacement": { "type": "string", "maxLength": 20000 }
              },
              "required": ["operation"],
              "additionalProperties": false
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        ApprovalPolicyDecision automatic = new ApprovalPolicyDecision(false, "");
        return new AgentToolDescriptor(
                "ledger_crud",
                new ToolPresentation("AI Ledger"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE,
                        "Maintain project-level working documents. Use list/read before updates. Reads return at most 20,000 Unicode characters. Updates atomically replace one character range and require the exact revision returned by read. The workspace holds at most 100 ledgers; each ledger holds at most 200,000 characters.",
                        SCHEMA),
                new ToolGovernancePolicy(automatic, 2, Map.of(
                        "list", new ToolOperationPolicy("list", automatic, 0),
                        "read", new ToolOperationPolicy("read", automatic, 0),
                        "create", new ToolOperationPolicy("create", automatic, 1),
                        "update", new ToolOperationPolicy("update", automatic, 1),
                        "delete", new ToolOperationPolicy("delete", automatic, 2)
                )));
    }
}
