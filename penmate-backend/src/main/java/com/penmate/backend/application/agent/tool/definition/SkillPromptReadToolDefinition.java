package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SkillPromptReadToolDefinition implements AgentToolDefinition {

    private static final String PARAMETERS_JSON_SCHEMA = """
            {
              \"type\": \"object\",
              \"properties\": {
                \"skill\": {
                  \"type\": \"string\",
                  \"description\": \"Skill name from the Available skills catalog, for example writer, planner, checker, editor, story_bible_query, or story_bible_guard\"
                }
              },
              \"required\": [\"skill\"],
              \"additionalProperties\": false
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "skill_prompt_read",
                new ToolPresentation("Skill Prompt Read"),
                new ToolExposure(
                        true,
                        "Read full skill prompt content by skill name when the main agent needs detailed instructions after seeing the Available skills catalog.",
                        PARAMETERS_JSON_SCHEMA
                ),
                new ToolGovernancePolicy(
                        new ApprovalPolicyDecision(false, ""),
                        1,
                        Map.of()
                )
        );
    }
}
