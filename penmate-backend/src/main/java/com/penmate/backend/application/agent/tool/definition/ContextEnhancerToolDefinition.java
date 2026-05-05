package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * [`context_enhancer`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/ContextEnhancerToolDefinition.java) 的静态定义。
 */
@Component
public class ContextEnhancerToolDefinition implements AgentToolDefinition {

    private static final String PARAMETERS_JSON_SCHEMA = """
            {
              \"type\": \"object\",
              \"properties\": {
                \"prompt\": {
                  \"type\": \"string\"
                }
              },
              \"required\": [\"prompt\"]
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "context_enhancer",
                new ToolPresentation("上下文增强"),
                new ToolExposure(true, "补充上下文", PARAMETERS_JSON_SCHEMA),
                new ToolGovernancePolicy(
                        new ApprovalPolicyDecision(false, ""),
                        1,
                        Map.of()
                )
        );
    }
}
