package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * story_bible_update tool 的静态定义。
 */
@Component
public class StoryBibleUpdateToolDefinition implements AgentToolDefinition {

    private static final String PARAMETERS_JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "operation": {
                  "type": "string",
                  "enum": ["create", "update", "delete", "list"]
                },
                "projectId": {
                  "type": "integer",
                  "minimum": 1
                },
                "chapterId": {
                  "type": "integer",
                  "minimum": 1
                },
                "entryId": {
                  "type": "integer",
                  "minimum": 1
                },
                "entryKey": {
                  "type": "string",
                  "minLength": 1,
                  "pattern": ".*\\S.*"
                },
                "entryType": {
                  "type": "string"
                },
                "title": {
                  "type": "string"
                },
                "content": {
                  "type": "string"
                },
                "canonicalStatus": {
                  "type": "string"
                },
                "riskLevel": {
                  "type": "integer",
                  "minimum": 0
                }
              },
              "required": ["operation"],
              "additionalProperties": false
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "story_bible_update",
                new ToolPresentation("故事圣经更新"),
                new ToolExposure(true, "对故事圣经条目执行新增、修改、删除或查询", PARAMETERS_JSON_SCHEMA),
                new ToolGovernancePolicy(
                        new ApprovalPolicyDecision(false, ""),
                        3,
                        Map.of(
                                "create", new ToolOperationPolicy("create", new ApprovalPolicyDecision(true, "STORY_BIBLE_CREATE")),
                                "update", new ToolOperationPolicy("update", new ApprovalPolicyDecision(true, "STORY_BIBLE_UPDATE")),
                                "delete", new ToolOperationPolicy("delete", new ApprovalPolicyDecision(true, "STORY_BIBLE_DELETE"))
                        )
                )
        );
    }
}
