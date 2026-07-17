package com.penmate.backend.application.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.storybible.StoryBibleApplicationService;
import com.penmate.backend.application.storybible.command.StoryBibleCommands;
import com.penmate.backend.domain.storybible.model.StoryBibleActorType;
import com.penmate.backend.domain.storybible.model.StoryBibleAlias;
import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.domain.storybible.model.StoryBibleCategory;
import com.penmate.backend.domain.storybible.model.StoryBibleInclusionPolicy;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.model.StoryBibleProgression;
import com.penmate.backend.domain.storybible.model.StoryBibleRelation;
import com.penmate.backend.domain.storybible.model.StoryBibleSemanticFamily;
import com.penmate.backend.domain.storybible.model.StoryBibleTag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DefaultStoryBibleUpdateApplicationService implements StoryBibleUpdateApplicationService {

    private static final int MAX_BATCH_SIZE = 32;

    private final StoryBibleApplicationService storyBibleApplicationService;
    private final ObjectMapper objectMapper;

    public DefaultStoryBibleUpdateApplicationService(StoryBibleApplicationService storyBibleApplicationService,
                                                       ObjectMapper objectMapper) {
        this.storyBibleApplicationService = storyBibleApplicationService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ToolCallResult execute(ToolCallRequest request) {
        assertRunIdentity(request);
        JsonNode envelope = parseEnvelope(request.toolArgsJson());
        JsonNode operations = envelope.get("operations");
        List<Map<String, Object>> results = new ArrayList<>(operations.size());
        for (int index = 0; index < operations.size(); index++) {
            JsonNode operation = operations.get(index);
            try {
                results.add(executeOperation(request, operation));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("operations[" + index + "] failed: " + message(ex), ex);
            }
        }
        return ToolCallResult.success(json(Map.of(
                "operation", "batch",
                "appliedCount", results.size(),
                "results", results
        )));
    }

    private JsonNode parseEnvelope(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root == null || !root.isObject()) throw new IllegalArgumentException("tool arguments must be a JSON object");
            if (!"batch".equals(root.path("operation").asText())) {
                throw new IllegalArgumentException("operation must be batch");
            }
            JsonNode operations = root.get("operations");
            if (operations == null || !operations.isArray() || operations.isEmpty()) {
                throw new IllegalArgumentException("operations must be a non-empty array");
            }
            if (operations.size() > MAX_BATCH_SIZE) {
                throw new IllegalArgumentException("operations must contain at most " + MAX_BATCH_SIZE + " items");
            }
            return root;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("tool arguments must be valid JSON", ex);
        }
    }

    private Map<String, Object> executeOperation(ToolCallRequest request, JsonNode operation) {
        if (operation == null || !operation.isObject()) {
            throw new IllegalArgumentException("mutation must be a JSON object");
        }
        String kind = requiredText(operation, "kind").toLowerCase(Locale.ROOT);
        return switch (kind) {
            case "create_node" -> createNode(request, kind, operation);
            case "update_node" -> updateNode(request, kind, operation);
            case "delete_node" -> deleteNode(request, kind, operation);
            case "create_relation" -> createRelation(request, kind, operation);
            case "update_relation" -> updateRelation(request, kind, operation);
            case "delete_relation" -> deleteRelation(request, kind, operation);
            case "create_progression" -> createProgression(request, kind, operation);
            case "update_progression" -> updateProgression(request, kind, operation);
            case "delete_progression" -> deleteProgression(request, kind, operation);
            case "create_node_type" -> createNodeType(request, kind, operation);
            case "update_node_type" -> updateNodeType(request, kind, operation);
            case "archive_node_type" -> archiveNodeType(request, kind, operation);
            case "create_category" -> createCategory(request, kind, operation);
            case "update_category" -> updateCategory(request, kind, operation);
            case "delete_category" -> deleteCategory(request, kind, operation);
            case "create_tag" -> createTag(request, kind, operation);
            case "update_tag" -> updateTag(request, kind, operation);
            case "delete_tag" -> deleteTag(request, kind, operation);
            default -> throw new IllegalArgumentException("unsupported mutation kind: " + kind);
        };
    }

    private Map<String, Object> createNode(ToolCallRequest request, String kind, JsonNode op) {
        StoryBibleNode node = storyBibleApplicationService.createNode(request.projectId(),
                new StoryBibleCommands.CreateNode(
                        requiredLong(op, "typeId"),
                        requiredText(op, "title"),
                        nullableText(op, "summary", null),
                        nullableText(op, "bodyMarkdown", null),
                        text(op, "attributesJson", "{}"),
                        enumValue(op, "inclusionPolicy", StoryBibleInclusionPolicy.class, null),
                        enumValue(op, "canonStatus", StoryBibleCanonStatus.class, null),
                        stringList(op, "aliases", List.of()),
                        longList(op, "categoryIds", List.of()),
                        longList(op, "tagIds", List.of())
                ), StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "NODE", node.getNodeId(), node.getRevision(), node);
    }

    private Map<String, Object> updateNode(ToolCallRequest request, String kind, JsonNode op) {
        Long nodeId = requiredLong(op, "nodeId");
        StoryBibleApplicationService.NodeDetails details = storyBibleApplicationService.getNodeDetails(request.projectId(), nodeId);
        StoryBibleNode existing = details.node();
        StoryBibleNode node = storyBibleApplicationService.updateNode(request.projectId(), nodeId,
                new StoryBibleCommands.UpdateNode(
                        requiredLong(op, "expectedRevision"),
                        optionalLong(op, "typeId", existing.getTypeId()),
                        text(op, "title", existing.getTitle()),
                        nullableText(op, "summary", existing.getSummary()),
                        nullableText(op, "bodyMarkdown", existing.getBodyMarkdown()),
                        text(op, "attributesJson", existing.getAttributesJson()),
                        enumValue(op, "inclusionPolicy", StoryBibleInclusionPolicy.class, existing.getInclusionPolicy()),
                        enumValue(op, "canonStatus", StoryBibleCanonStatus.class, existing.getCanonStatus()),
                        stringList(op, "aliases", details.aliases().stream().map(StoryBibleAlias::getAlias).toList()),
                        longList(op, "categoryIds", details.categoryIds()),
                        longList(op, "tagIds", details.tagIds())
                ), StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "NODE", nodeId, node.getRevision(), node);
    }

    private Map<String, Object> deleteNode(ToolCallRequest request, String kind, JsonNode op) {
        Long nodeId = requiredLong(op, "nodeId");
        storyBibleApplicationService.deleteNode(request.projectId(), nodeId, requiredLong(op, "expectedRevision"),
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "NODE", nodeId, null, null);
    }

    private Map<String, Object> createRelation(ToolCallRequest request, String kind, JsonNode op) {
        StoryBibleRelation relation = storyBibleApplicationService.createRelation(request.projectId(),
                new StoryBibleCommands.CreateRelation(
                        requiredLong(op, "sourceNodeId"), requiredText(op, "relationType"),
                        requiredLong(op, "targetNodeId"), nullableText(op, "description", null),
                        text(op, "attributesJson", "{}")
                ), StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "RELATION", relation.getRelationId(), relation.getRevision(), relation);
    }

    private Map<String, Object> updateRelation(ToolCallRequest request, String kind, JsonNode op) {
        Long relationId = requiredLong(op, "relationId");
        StoryBibleRelation existing = requireRelation(request.projectId(), relationId);
        StoryBibleRelation relation = storyBibleApplicationService.updateRelation(request.projectId(), relationId,
                new StoryBibleCommands.UpdateRelation(
                        requiredLong(op, "expectedRevision"),
                        text(op, "relationType", existing.getRelationType()),
                        optionalLong(op, "targetNodeId", existing.getTargetNodeId()),
                        nullableText(op, "description", existing.getDescription()),
                        text(op, "attributesJson", existing.getAttributesJson())
                ), StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "RELATION", relationId, relation.getRevision(), relation);
    }

    private Map<String, Object> deleteRelation(ToolCallRequest request, String kind, JsonNode op) {
        Long relationId = requiredLong(op, "relationId");
        storyBibleApplicationService.deleteRelation(request.projectId(), relationId, requiredLong(op, "expectedRevision"),
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "RELATION", relationId, null, null);
    }

    private Map<String, Object> createProgression(ToolCallRequest request, String kind, JsonNode op) {
        StoryBibleProgression progression = storyBibleApplicationService.createProgression(request.projectId(),
                new StoryBibleCommands.CreateProgression(
                        requiredLong(op, "nodeId"), requiredLong(op, "anchorChapterId"),
                        nullableLong(op, "endChapterId", null), nullableLong(op, "storyEventNodeId", null),
                        requiredText(op, "patchJson"), nullableText(op, "summary", null)
                ), StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "PROGRESSION", progression.getProgressionId(), progression.getRevision(), progression);
    }

    private Map<String, Object> updateProgression(ToolCallRequest request, String kind, JsonNode op) {
        Long progressionId = requiredLong(op, "progressionId");
        StoryBibleProgression existing = requireProgression(request.projectId(), progressionId);
        StoryBibleProgression progression = storyBibleApplicationService.updateProgression(request.projectId(), progressionId,
                new StoryBibleCommands.UpdateProgression(
                        requiredLong(op, "expectedRevision"),
                        optionalLong(op, "anchorChapterId", existing.getAnchorChapterId()),
                        nullableLong(op, "endChapterId", existing.getEndChapterId()),
                        nullableLong(op, "storyEventNodeId", existing.getStoryEventNodeId()),
                        text(op, "patchJson", existing.getPatchJson()),
                        nullableText(op, "summary", existing.getSummary())
                ), StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "PROGRESSION", progressionId, progression.getRevision(), progression);
    }

    private Map<String, Object> deleteProgression(ToolCallRequest request, String kind, JsonNode op) {
        Long progressionId = requiredLong(op, "progressionId");
        storyBibleApplicationService.deleteProgression(request.projectId(), progressionId, requiredLong(op, "expectedRevision"),
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "PROGRESSION", progressionId, null, null);
    }

    private Map<String, Object> createNodeType(ToolCallRequest request, String kind, JsonNode op) {
        StoryBibleNodeType type = storyBibleApplicationService.createNodeType(request.projectId(),
                new StoryBibleCommands.CreateNodeType(
                        requiredText(op, "typeCode"), requiredEnum(op, "semanticFamily", StoryBibleSemanticFamily.class),
                        requiredText(op, "displayName"), nullableText(op, "iconCode", null),
                        text(op, "fieldSchemaJson", "{}"), integer(op, "sortOrder", 0)
                ), StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "NODE_TYPE", type.getTypeId(), null, type);
    }

    private Map<String, Object> updateNodeType(ToolCallRequest request, String kind, JsonNode op) {
        Long typeId = requiredLong(op, "typeId");
        StoryBibleNodeType existing = requireNodeType(request.projectId(), typeId);
        StoryBibleNodeType type = storyBibleApplicationService.updateNodeType(request.projectId(), typeId,
                new StoryBibleCommands.UpdateNodeType(
                        text(op, "displayName", existing.getDisplayName()),
                        nullableText(op, "iconCode", existing.getIconCode()),
                        text(op, "fieldSchemaJson", existing.getFieldSchemaJson()),
                        integer(op, "sortOrder", existing.getSortOrder())
                ), StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "NODE_TYPE", typeId, null, type);
    }

    private Map<String, Object> archiveNodeType(ToolCallRequest request, String kind, JsonNode op) {
        Long typeId = requiredLong(op, "typeId");
        storyBibleApplicationService.archiveNodeType(request.projectId(), typeId,
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "NODE_TYPE", typeId, null, null);
    }

    private Map<String, Object> createCategory(ToolCallRequest request, String kind, JsonNode op) {
        StoryBibleCategory category = storyBibleApplicationService.createCategory(request.projectId(),
                new StoryBibleCommands.CreateCategory(nullableLong(op, "parentCategoryId", null),
                        requiredText(op, "name"), integer(op, "sortOrder", 0)),
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "CATEGORY", category.getCategoryId(), null, category);
    }

    private Map<String, Object> updateCategory(ToolCallRequest request, String kind, JsonNode op) {
        Long categoryId = requiredLong(op, "categoryId");
        StoryBibleCategory existing = requireCategory(request.projectId(), categoryId);
        StoryBibleCategory category = storyBibleApplicationService.updateCategory(request.projectId(), categoryId,
                new StoryBibleCommands.UpdateCategory(
                        nullableLong(op, "parentCategoryId", existing.getParentCategoryId()),
                        text(op, "name", existing.getName()), integer(op, "sortOrder", existing.getSortOrder())
                ), StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "CATEGORY", categoryId, null, category);
    }

    private Map<String, Object> deleteCategory(ToolCallRequest request, String kind, JsonNode op) {
        Long categoryId = requiredLong(op, "categoryId");
        storyBibleApplicationService.deleteCategory(request.projectId(), categoryId,
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "CATEGORY", categoryId, null, null);
    }

    private Map<String, Object> createTag(ToolCallRequest request, String kind, JsonNode op) {
        StoryBibleTag tag = storyBibleApplicationService.createTag(request.projectId(),
                new StoryBibleCommands.CreateTag(requiredText(op, "name"), nullableText(op, "color", null)),
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "TAG", tag.getTagId(), null, tag);
    }

    private Map<String, Object> updateTag(ToolCallRequest request, String kind, JsonNode op) {
        Long tagId = requiredLong(op, "tagId");
        StoryBibleTag existing = requireTag(request.projectId(), tagId);
        StoryBibleTag tag = storyBibleApplicationService.updateTag(request.projectId(), tagId,
                new StoryBibleCommands.UpdateTag(text(op, "name", existing.getName()),
                        nullableText(op, "color", existing.getColor())),
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "TAG", tagId, null, tag);
    }

    private Map<String, Object> deleteTag(ToolCallRequest request, String kind, JsonNode op) {
        Long tagId = requiredLong(op, "tagId");
        storyBibleApplicationService.deleteTag(request.projectId(), tagId,
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "TAG", tagId, null, null);
    }

    private StoryBibleRelation requireRelation(Long projectId, Long relationId) {
        return storyBibleApplicationService.listRelations(projectId, null).stream()
                .filter(item -> relationId.equals(item.getRelationId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Story Bible relation not found"));
    }

    private StoryBibleProgression requireProgression(Long projectId, Long progressionId) {
        return storyBibleApplicationService.listProgressions(projectId, null).stream()
                .filter(item -> progressionId.equals(item.getProgressionId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Story Bible progression not found"));
    }

    private StoryBibleNodeType requireNodeType(Long projectId, Long typeId) {
        return storyBibleApplicationService.listNodeTypes(projectId).stream()
                .filter(item -> typeId.equals(item.getTypeId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Story Bible node type not found"));
    }

    private StoryBibleCategory requireCategory(Long projectId, Long categoryId) {
        return storyBibleApplicationService.listCategories(projectId).stream()
                .filter(item -> categoryId.equals(item.getCategoryId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Story Bible category not found"));
    }

    private StoryBibleTag requireTag(Long projectId, Long tagId) {
        return storyBibleApplicationService.listTags(projectId).stream()
                .filter(item -> tagId.equals(item.getTagId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Story Bible tag not found"));
    }

    private Map<String, Object> result(String kind, String entityType, Long entityId, Long revision, Object entity) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("kind", kind);
        value.put("entityType", entityType);
        value.put("entityId", String.valueOf(entityId));
        value.put("status", kind.startsWith("delete_") || kind.startsWith("archive_") ? "deleted" : "applied");
        if (revision != null) value.put("revision", revision);
        if (entity != null) value.put("entity", entity);
        return value;
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field, null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private String nullableText(JsonNode node, String field, String fallback) {
        if (!node.has(field)) return fallback;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Long requiredLong(JsonNode node, String field) {
        Long value = nullableLong(node, field, null);
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private Long optionalLong(JsonNode node, String field, Long fallback) {
        if (!node.has(field)) return fallback;
        Long value = parsePositiveLong(node.get(field), field);
        return value == null ? fallback : value;
    }

    private Long nullableLong(JsonNode node, String field, Long fallback) {
        if (!node.has(field)) return fallback;
        return parsePositiveLong(node.get(field), field);
    }

    private Long parsePositiveLong(JsonNode value, String field) {
        if (value == null || value.isNull()) return null;
        try {
            long parsed = value.isIntegralNumber() ? value.longValue() : Long.parseLong(value.asText().trim());
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
    }

    private Integer integer(JsonNode node, String field, Integer fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return fallback;
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return value.intValue();
    }

    private List<String> stringList(JsonNode node, String field, List<String> fallback) {
        JsonNode value = node.get(field);
        if (value == null) return fallback;
        if (!value.isArray()) throw new IllegalArgumentException(field + " must be an array");
        List<String> result = new ArrayList<>();
        value.forEach(item -> {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new IllegalArgumentException(field + " must contain non-blank strings");
            }
            result.add(item.asText().trim());
        });
        return List.copyOf(result);
    }

    private List<Long> longList(JsonNode node, String field, List<Long> fallback) {
        JsonNode value = node.get(field);
        if (value == null) return fallback;
        if (!value.isArray()) throw new IllegalArgumentException(field + " must be an array");
        List<Long> result = new ArrayList<>();
        value.forEach(item -> result.add(parsePositiveLong(item, field)));
        return List.copyOf(result);
    }

    private <E extends Enum<E>> E enumValue(JsonNode node, String field, Class<E> type, E fallback) {
        String value = text(node, field, null);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(field + " has an unsupported value");
        }
    }

    private <E extends Enum<E>> E requiredEnum(JsonNode node, String field, Class<E> type) {
        E value = enumValue(node, field, type, null);
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize Story Bible mutation result", ex);
        }
    }

    private String message(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    private void assertRunIdentity(ToolCallRequest request) {
        if (request == null || request.projectId() == null || request.runId() == null || request.operatorId() == null) {
            throw new IllegalStateException("run context is required for Story Bible mutations");
        }
    }
}
