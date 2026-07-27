package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

public final class StoryBibleV2ToolDefinitions {
    private StoryBibleV2ToolDefinitions() { }

    public static AgentToolDefinition inspect() { return new StoryBibleInspectToolDefinition(); }
    public static AgentToolDefinition nodeWrite() { return new StoryBibleNodeWriteToolDefinition(); }
    public static AgentToolDefinition relationWrite() { return new StoryBibleRelationWriteToolDefinition(); }
    public static AgentToolDefinition progressionWrite() { return new StoryBibleProgressionWriteToolDefinition(); }
    public static AgentToolDefinition structureWrite() { return new StoryBibleStructureWriteToolDefinition(); }

    static ToolGovernancePolicy readOnly() {
        return new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 0, Map.of());
    }

    static ToolGovernancePolicy write(String reason, int riskLevel, String... operations) {
        ApprovalPolicyDecision approval = new ApprovalPolicyDecision(true, reason);
        Map<String, ToolOperationPolicy> policies = java.util.Arrays.stream(operations)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        operation -> operation,
                        operation -> new ToolOperationPolicy(operation, approval)));
        return new ToolGovernancePolicy(approval, riskLevel, policies);
    }
}

@Component
class StoryBibleInspectToolDefinition implements AgentToolDefinition {
    private static final String SCHEMA = """
            {
              "type":"object",
              "properties":{
                "operation":{"type":"string","enum":["overview","types","nodes","node"]},
                "nodeId":{"type":"integer","minimum":1,"description":"Required only for operation=node. Use an exact nodeId returned by search, nodes, or overview."},
                "typeCode":{"type":"string","minLength":1,"description":"Optional types/nodes filter. Use only an exact typeCode returned by operation=types; never guess this value."},
                "semanticFamily":{"type":"string","enum":["CORE","CHARACTER","WORLD","THING","NARRATIVE","TIMELINE"],"description":"Optional types/nodes filter by broad semantic family."},
                "query":{"type":"string","description":"Optional title or summary filter for operation=nodes."},
                "canonStatus":{"type":"string","enum":["DRAFT","CANON","ARCHIVED"],"description":"Optional status filter for operation=nodes."},
                "offset":{"type":"integer","minimum":0,"description":"Optional nodes pagination offset."},
                "limit":{"type":"integer","minimum":1,"maximum":100,"description":"Optional nodes page size; defaults to 50."}
              },
              "required":["operation"],
              "additionalProperties":false
            }
            """;

    @Override public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "story_bible_inspect",
                new ToolPresentation("故事设定精确读取"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE,
                        "Inspect the factual Story Bible. Use types for the type catalog, nodes to enumerate exact nodeId/revision values with stable pagination, and node for one complete node. Never guess nodeId or typeCode. Overview reports counts, Story Core fields, structural issues, and recent changes without claiming semantic completeness.",
                        SCHEMA),
                StoryBibleV2ToolDefinitions.readOnly());
    }
}

@Component
class StoryBibleNodeWriteToolDefinition implements AgentToolDefinition {
    private static final String SCHEMA = """
            {
              "type":"object","properties":{"items":{"type":"array","minItems":1,"maxItems":25,"items":{
              "type":"object","properties":{
                "operation":{"type":"string","enum":["create","update","archive"]},
                "nodeId":{"type":"integer","minimum":1},
                "expectedRevision":{"type":"integer","minimum":1},
                "typeId":{"type":"integer","minimum":1},
                "title":{"type":"string","minLength":1},
                "summary":{"type":["string","null"]},
                "bodyMarkdown":{"type":["string","null"]},
                "attributes":{"type":"object","additionalProperties":true},
                "inclusionPolicy":{"type":"string","enum":["ALWAYS_INCLUDE","AUTO_RETRIEVE","MANUAL_ONLY"]},
                "canonStatus":{"type":"string","enum":["DRAFT","CANON","ARCHIVED"]},
                "aliases":{"type":"array","items":{"type":"string","minLength":1}},
                "categoryIds":{"type":"array","items":{"type":"integer","minimum":1}},
                "tagIds":{"type":"array","items":{"type":"integer","minimum":1}}
              },
              "required":["operation"],"additionalProperties":false
              }}},"required":["items"],"additionalProperties":false
            }
            """;

    @Override public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "story_bible_node_write",
                new ToolPresentation("故事设定节点写入"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE,
                        "Atomically create, minimally update, or archive 1-25 Story Bible nodes. Pass items as an array. Updates and archives require exact revisions; the whole batch rolls back if any item fails.",
                        SCHEMA),
                StoryBibleV2ToolDefinitions.write("STORY_BIBLE_NODE_MUTATION", 3,
                        "create", "update", "archive"));
    }
}

@Component
class StoryBibleRelationWriteToolDefinition implements AgentToolDefinition {
    private static final String SCHEMA = """
            {
              "type":"object","properties":{"items":{"type":"array","minItems":1,"maxItems":25,"items":{
              "type":"object","properties":{
                "operation":{"type":"string","enum":["create","update","delete"]},
                "relationId":{"type":"integer","minimum":1},
                "expectedRevision":{"type":"integer","minimum":1},
                "sourceNodeId":{"type":"integer","minimum":1},
                "targetNodeId":{"type":"integer","minimum":1},
                "relationType":{"type":"string","minLength":1},
                "description":{"type":["string","null"]},
                "attributes":{"type":"object","additionalProperties":true}
              },
              "required":["operation"],"additionalProperties":false
              }}},"required":["items"],"additionalProperties":false
            }
            """;

    @Override public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "story_bible_relation_write",
                new ToolPresentation("故事设定关系写入"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE,
                        "Atomically create, minimally update, or delete 1-25 Story Bible relations. Updates and deletes require exact revisions; the whole batch rolls back if any item fails.",
                        SCHEMA),
                StoryBibleV2ToolDefinitions.write("STORY_BIBLE_RELATION_MUTATION", 3,
                        "create", "update", "delete"));
    }
}

@Component
class StoryBibleProgressionWriteToolDefinition implements AgentToolDefinition {
    private static final String SCHEMA = """
            {
              "type":"object","properties":{"items":{"type":"array","minItems":1,"maxItems":25,"items":{
              "type":"object","properties":{
                "operation":{"type":"string","enum":["create","update","delete"]},
                "progressionId":{"type":"integer","minimum":1},
                "expectedRevision":{"type":"integer","minimum":1},
                "nodeId":{"type":"integer","minimum":1},
                "anchorChapterId":{"type":"integer","minimum":1},
                "endChapterId":{"type":["integer","null"],"minimum":1},
                "storyEventNodeId":{"type":["integer","null"],"minimum":1},
                "patch":{"type":"array","minItems":1,"items":{"type":"object"}},
                "summary":{"type":["string","null"]}
              },
              "required":["operation"],"additionalProperties":false
              }}},"required":["items"],"additionalProperties":false
            }
            """;

    @Override public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "story_bible_progression_write",
                new ToolPresentation("故事设定演化写入"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE,
                        "Atomically create, minimally update, or delete 1-25 chapter-scoped Story Bible progressions. Pass patches as RFC 6902 operations. The whole batch rolls back if any item fails.",
                        SCHEMA),
                StoryBibleV2ToolDefinitions.write("STORY_BIBLE_PROGRESSION_MUTATION", 3,
                        "create", "update", "delete"));
    }
}

@Component
class StoryBibleStructureWriteToolDefinition implements AgentToolDefinition {
    private static final String SCHEMA = """
            {
              "type":"object","properties":{"items":{"type":"array","minItems":1,"maxItems":25,"items":{
              "type":"object","properties":{
                "operation":{"type":"string","enum":["create_type","update_type","archive_type","create_category","update_category","delete_category","create_tag","update_tag","delete_tag"]},
                "typeId":{"type":"integer","minimum":1},
                "categoryId":{"type":"integer","minimum":1},
                "tagId":{"type":"integer","minimum":1},
                "typeCode":{"type":"string","minLength":1},
                "semanticFamily":{"type":"string","enum":["CORE","CHARACTER","WORLD","THING","NARRATIVE","TIMELINE"]},
                "displayName":{"type":"string","minLength":1},
                "iconCode":{"type":["string","null"]},
                "fieldSchema":{"type":"object"},
                "parentCategoryId":{"type":["integer","null"],"minimum":1},
                "name":{"type":"string","minLength":1},
                "color":{"type":["string","null"]},
                "sortOrder":{"type":"integer"}
              },
              "required":["operation"],"additionalProperties":false
              }}},"required":["items"],"additionalProperties":false
            }
            """;

    @Override public AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "story_bible_structure_write",
                new ToolPresentation("故事设定结构管理"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE,
                        "Atomically manage 1-25 custom Story Bible types, categories, or tags after calling story_bible_inspect with operation=types and no filters. Never structurally edit system types; the whole batch rolls back if any item fails.",
                        SCHEMA),
                StoryBibleV2ToolDefinitions.write("STORY_BIBLE_STRUCTURE_MUTATION", 4,
                        "create_type", "update_type", "archive_type", "create_category", "update_category",
                        "delete_category", "create_tag", "update_tag", "delete_tag"));
    }
}
