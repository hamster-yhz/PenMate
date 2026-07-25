package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

public final class ChapterContentToolDefinitions {
    private ChapterContentToolDefinitions() {
    }

    public static AgentToolDefinition read() {
        return new ChapterReadToolDefinition();
    }

    public static AgentToolDefinition replace() {
        return new ChapterReplaceToolDefinition();
    }

    public static AgentToolDefinition patch() {
        return new ChapterPatchToolDefinition();
    }

    static ToolGovernancePolicy readOnly() {
        return new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 0, Map.of());
    }

    static ToolGovernancePolicy write() {
        return new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 2, Map.of());
    }
}

@Component
class ChapterReadToolDefinition implements AgentToolDefinition {
    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {},
              "additionalProperties": false
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "chapter_read",
                new ToolPresentation("Read chapter"),
                new ToolExposure(
                        ToolLifecycleStatus.ACTIVE,
                        "Read the exact active chapter content and its current revision and SHA-256 hash. "
                                + "Call this before chapter_replace or chapter_patch and again when a write reports a conflict.",
                        SCHEMA,
                        Set.of("default", "rewrite")),
                ChapterContentToolDefinitions.readOnly());
    }
}

@Component
class ChapterReplaceToolDefinition implements AgentToolDefinition {
    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "expectedRevision": { "type": "integer", "minimum": 1 },
                "expectedContentHash": { "type": "string", "pattern": "^[0-9a-f]{64}$" },
                "content": { "type": "string" }
              },
              "required": ["expectedRevision", "expectedContentHash", "content"],
              "additionalProperties": false
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "chapter_replace",
                new ToolPresentation("Replace chapter"),
                new ToolExposure(
                        ToolLifecycleStatus.ACTIVE,
                        "Atomically replace the active chapter with the exact content supplied. "
                                + "Use the revision and hash returned by chapter_read or the preceding successful chapter write. "
                                + "This tool performs no rewriting or interpretation: supplied content is persisted verbatim.",
                        SCHEMA,
                        Set.of("default", "rewrite")),
                ChapterContentToolDefinitions.write());
    }
}

@Component
class ChapterPatchToolDefinition implements AgentToolDefinition {
    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "expectedRevision": { "type": "integer", "minimum": 1 },
                "expectedContentHash": { "type": "string", "pattern": "^[0-9a-f]{64}$" },
                "replacements": {
                  "type": "array",
                  "minItems": 1,
                  "items": {
                    "type": "object",
                    "properties": {
                      "oldText": { "type": "string", "minLength": 1 },
                      "newText": { "type": "string" },
                      "expectedOccurrences": { "type": "integer", "minimum": 1 }
                    },
                    "required": ["oldText", "newText", "expectedOccurrences"],
                    "additionalProperties": false
                  }
                }
              },
              "required": ["expectedRevision", "expectedContentHash", "replacements"],
              "additionalProperties": false
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "chapter_patch",
                new ToolPresentation("Patch chapter"),
                new ToolExposure(
                        ToolLifecycleStatus.ACTIVE,
                        "Atomically apply ordered exact-text replacements to the active chapter. "
                                + "Every oldText must occur exactly expectedOccurrences times at its step or the entire call is rejected without writing. "
                                + "Use one call for one requested state transition and never encode multiple editing stages as prose instructions.",
                        SCHEMA,
                        Set.of("default", "rewrite")),
                ChapterContentToolDefinitions.write());
    }
}
