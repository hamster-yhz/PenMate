package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StoryBibleUpdateToolDefinition implements AgentToolDefinition {

    private static final String PARAMETERS_JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "operation": {
                  "type": "string",
                  "const": "batch",
                  "description": "Always batch. The complete ordered batch is reviewed in one approval."
                },
                "operations": {
                  "type": "array",
                  "minItems": 1,
                  "maxItems": 32,
                  "items": { "$ref": "#/$defs/mutation" }
                }
              },
              "required": ["operation", "operations"],
              "additionalProperties": false,
              "$defs": {
                "id": { "type": "integer", "minimum": 1 },
                "revision": { "type": "integer", "minimum": 1 },
                "stringList": { "type": "array", "items": { "type": "string" } },
                "idList": { "type": "array", "items": { "$ref": "#/$defs/id" } },
                "mutation": {
                  "type": "object",
                  "description": "Ordered mutation. Required fields are validated for its kind before execution.",
                  "properties": {
                    "kind": {
                      "type": "string",
                      "enum": [
                        "create_node", "update_node", "delete_node",
                        "create_relation", "update_relation", "delete_relation",
                        "create_progression", "update_progression", "delete_progression",
                        "create_node_type", "update_node_type", "archive_node_type",
                        "create_category", "update_category", "delete_category",
                        "create_tag", "update_tag", "delete_tag"
                      ]
                    },
                    "nodeId": { "$ref": "#/$defs/id" },
                    "typeId": { "$ref": "#/$defs/id" },
                    "relationId": { "$ref": "#/$defs/id" },
                    "progressionId": { "$ref": "#/$defs/id" },
                    "categoryId": { "$ref": "#/$defs/id" },
                    "tagId": { "$ref": "#/$defs/id" },
                    "expectedRevision": { "$ref": "#/$defs/revision" },
                    "typeCode": { "type": "string", "minLength": 1 },
                    "semanticFamily": { "type": "string", "enum": ["CORE", "CHARACTER", "WORLD", "THING", "NARRATIVE", "TIMELINE"] },
                    "displayName": { "type": "string", "minLength": 1 },
                    "iconCode": { "type": ["string", "null"] },
                    "fieldSchemaJson": { "type": "string" },
                    "sortOrder": { "type": "integer" },
                    "title": { "type": "string", "minLength": 1 },
                    "summary": { "type": ["string", "null"] },
                    "bodyMarkdown": { "type": ["string", "null"] },
                    "attributesJson": { "type": "string" },
                    "inclusionPolicy": { "type": "string", "enum": ["ALWAYS_INCLUDE", "AUTO_RETRIEVE", "MANUAL_ONLY"] },
                    "canonStatus": { "type": "string", "enum": ["DRAFT", "CANON", "ARCHIVED"] },
                    "aliases": { "$ref": "#/$defs/stringList" },
                    "categoryIds": { "$ref": "#/$defs/idList" },
                    "tagIds": { "$ref": "#/$defs/idList" },
                    "sourceNodeId": { "$ref": "#/$defs/id" },
                    "targetNodeId": { "$ref": "#/$defs/id" },
                    "relationType": { "type": "string", "minLength": 1 },
                    "description": { "type": ["string", "null"] },
                    "anchorChapterId": { "$ref": "#/$defs/id" },
                    "endChapterId": { "anyOf": [{ "$ref": "#/$defs/id" }, { "type": "null" }] },
                    "storyEventNodeId": { "anyOf": [{ "$ref": "#/$defs/id" }, { "type": "null" }] },
                    "patchJson": { "type": "string" },
                    "parentCategoryId": { "anyOf": [{ "$ref": "#/$defs/id" }, { "type": "null" }] },
                    "name": { "type": "string", "minLength": 1 },
                    "color": { "type": ["string", "null"] }
                  },
                  "required": ["kind"],
                  "additionalProperties": false
                }
              }
            }
            """;

    @Override
    public AgentToolDescriptor descriptor() {
        ApprovalPolicyDecision approval = new ApprovalPolicyDecision(true, "STORY_BIBLE_BATCH_MUTATION");
        return new AgentToolDescriptor(
                "story_bible_update",
                new ToolPresentation("故事设定更新"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE,
                        "Apply an ordered batch of Story Bible mutations after one user approval. Use story_bible_search for reads.",
                        PARAMETERS_JSON_SCHEMA,
                        java.util.Set.of("default", "world-build")),
                new ToolGovernancePolicy(
                        approval,
                        3,
                        Map.of("batch", new ToolOperationPolicy("batch", approval))
                )
        );
    }
}
