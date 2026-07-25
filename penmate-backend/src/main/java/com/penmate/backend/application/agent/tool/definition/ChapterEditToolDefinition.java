package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChapterEditToolDefinition implements AgentToolDefinition {

    private static final String PARAMETERS_JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "chapterId": {
                  "type": "integer",
                  "description": "要正式修改并保存的章节 ID"
                },
                "instruction": {
                  "type": "string",
                  "description": "对该章节正文的具体修改要求"
                }
              },
              "required": ["chapterId", "instruction"],
              "additionalProperties": false
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "chapter_edit",
                new ToolPresentation("编辑章节正文"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE,
                        "正式编辑并保存指定章节正文。调用后会独占锁定该章节、读取最新正文、按 instruction 流式改写，成功时原子保存；失败或取消不会改动正文。一次只编辑一个章节，需要修改多章时按章依次调用。",
                        PARAMETERS_JSON_SCHEMA,
                        java.util.Set.of("default", "rewrite")),
                new ToolGovernancePolicy(
                        new ApprovalPolicyDecision(false, ""),
                        2,
                        Map.of()
                )
        );
    }
}
