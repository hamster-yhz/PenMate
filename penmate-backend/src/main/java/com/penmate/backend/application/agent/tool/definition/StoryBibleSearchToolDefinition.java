package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StoryBibleSearchToolDefinition implements AgentToolDefinition {
    private static final String SCHEMA = """
            {
              "type":"object",
              "properties":{
                "query":{"type":"string","minLength":1},
                "mentionedEntities":{"type":"array","items":{"type":"string"}}
              },
              "required":["query"],
              "additionalProperties":false
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "story_bible_search",
                new ToolPresentation("Story Bible Search"),
                new ToolExposure(true, "Search the Run-bound Story Bible context at the active manuscript chapter", SCHEMA),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 0, Map.of())
        );
    }
}
