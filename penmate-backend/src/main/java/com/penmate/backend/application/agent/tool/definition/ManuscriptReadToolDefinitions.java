package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

public final class ManuscriptReadToolDefinitions {
    private ManuscriptReadToolDefinitions() {}
    public static AgentToolDefinition manifest() { return new ManuscriptManifestToolDefinition(); }
    public static AgentToolDefinition chapterRead() { return new ManuscriptChapterReadToolDefinition(); }
}

@Component
class ManuscriptManifestToolDefinition implements AgentToolDefinition {
    private static final String SCHEMA = """
            {"type":"object","properties":{"cursor":{"type":"integer","minimum":0},"limit":{"type":"integer","minimum":1,"maximum":200}},"additionalProperties":false}
            """;
    @Override public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor("manuscript_manifest", new ToolPresentation("Manuscript manifest"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE,
                        "List the exact manuscript structure with ordered volume/chapter IDs, revisions, SHA-256 hashes, and Unicode character counts. Results are paginated with nextCursor.", SCHEMA),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 0, Map.of()));
    }
}

@Component
class ManuscriptChapterReadToolDefinition implements AgentToolDefinition {
    private static final String SCHEMA = """
            {
              "type":"object",
              "properties":{
                "selections":{"type":"array","minItems":1,"maxItems":50,"items":{
                  "type":"object","properties":{
                    "chapterId":{"type":"integer","minimum":1},
                    "start":{"type":"integer","minimum":0},
                    "end":{"type":"integer","minimum":0}
                  },"required":["chapterId"],"additionalProperties":false
                }}
              },
              "required":["selections"],"additionalProperties":false
            }
            """;
    @Override public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor("manuscript_chapter_read", new ToolPresentation("Read manuscript chapters"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE,
                        "Read one or more arbitrary manuscript chapter ranges selected from manuscript_manifest. Up to 50 selections and 20,000 returned Unicode characters per call. Requests over the limit succeed with truncated=true and exact nextSelections for continuation; continue with those ranges when more text is needed.", SCHEMA),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 0, Map.of()));
    }
}
