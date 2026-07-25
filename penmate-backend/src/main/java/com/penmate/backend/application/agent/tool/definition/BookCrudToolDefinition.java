package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * [`book_crud`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/BookCrudToolDefinition.java) 的静态定义。
 * <p>schema、展示文案与操作级治理策略都在此声明，避免散落在 catalog/source 的多处硬编码。</p>
 */
@Component
public class BookCrudToolDefinition implements AgentToolDefinition {

    private static final String PARAMETERS_JSON_SCHEMA = """
            {
              \"type\": \"object\",
              \"properties\": {
                \"operation\": {
                  \"type\": \"string\",
                  \"enum\": [\"create\", \"list\", \"update\", \"delete\"],
                  \"description\": \"create 需要 ownerUserId 和 title；update/delete 需要 projectId；list 仅需 operation\"
                },
                \"ownerUserId\": {
                  \"type\": \"integer\"
                },
                \"projectId\": {
                  \"type\": \"integer\"
                },
                \"title\": {
                  \"type\": \"string\"
                },
                \"summary\": {
                  \"type\": \"string\"
                },
                \"status\": {
                  \"type\": \"integer\"
                }
              },
              \"required\": [\"operation\"],
              \"additionalProperties\": false
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "book_crud",
                new ToolPresentation("书籍 CRUD"),
                new ToolExposure(ToolLifecycleStatus.DRAINING, "书籍 CRUD；必须提供 operation，并按 create/list/update/delete 传入对应字段", PARAMETERS_JSON_SCHEMA),
                new ToolGovernancePolicy(
                        new ApprovalPolicyDecision(false, ""),
                        2,
                        Map.of(
                                "delete", new ToolOperationPolicy(
                                        "delete",
                                        new ApprovalPolicyDecision(true, "BOOK_DELETE")
                                )
                        )
                )
        );
    }
}
