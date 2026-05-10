package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * rag_query tool 的静态定义。
 */
@Component
public class RagQueryToolDefinition implements AgentToolDefinition {

    private static final String PARAMETERS_JSON_SCHEMA = """
            {
              \"type\": \"object\",
              \"properties\": {
                \"query\": {
                  \"type\": \"string\"
                }
              },
              \"required\": [\"query\"]
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "rag_query",
                new ToolPresentation("RAG 查询"),
                new ToolExposure(true, "按需查询知识库并返回可引用片段", PARAMETERS_JSON_SCHEMA),
                new ToolGovernancePolicy(
                        new ApprovalPolicyDecision(false, ""),
                        1,
                        Map.of()
                )
        );
    }
}
