package com.penmate.backend.application.agent.tool;

import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.common.serialization.JsonCodec;
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
    private final JsonCodec jsonCodec;

    public DefaultStoryBibleUpdateApplicationService(StoryBibleApplicationService storyBibleApplicationService,
                                                       JsonCodec jsonCodec) {
        this.storyBibleApplicationService = storyBibleApplicationService;
        this.jsonCodec = jsonCodec;
    }

    @Override
    @Transactional
    public ToolCallResult execute(ToolCallRequest request) {
        assertRunIdentity(request);
        List<?> operations = parseOperations(request.toolArgsJson());
        List<Map<String, Object>> results = new ArrayList<>(operations.size());
        for (int index = 0; index < operations.size(); index++) {
            Object operation = operations.get(index);
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

    private List<?> parseOperations(String rawJson) {
        try {
            Object decoded = jsonCodec.read(rawJson);
            if (!(decoded instanceof Map<?, ?> values)) {
                throw new IllegalArgumentException("tool arguments must be a JSON object");
            }
            Map<String, Object> root = stringKeyMap(values);
            if (!"batch".equals(text(root, "operation", null))) {
                throw new IllegalArgumentException("operation must be batch");
            }
            Object rawOperations = root.get("operations");
            if (!(rawOperations instanceof List<?> operations) || operations.isEmpty()) {
                throw new IllegalArgumentException("operations must be a non-empty array");
            }
            if (operations.size() > MAX_BATCH_SIZE) {
                throw new IllegalArgumentException("operations must contain at most " + MAX_BATCH_SIZE + " items");
            }
            return operations;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("tool arguments must be valid JSON", ex);
        }
    }

    private Map<String, Object> executeOperation(ToolCallRequest request, Object rawOperation) {
        if (!(rawOperation instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException("mutation must be a JSON object");
        }
        Map<String, Object> operation = stringKeyMap(values);
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

    private Map<String, Object> createNode(ToolCallRequest request, String kind, Map<String, Object> op) {
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

    private Map<String, Object> updateNode(ToolCallRequest request, String kind, Map<String, Object> op) {
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

    private Map<String, Object> deleteNode(ToolCallRequest request, String kind, Map<String, Object> op) {
        Long nodeId = requiredLong(op, "nodeId");
        storyBibleApplicationService.deleteNode(request.projectId(), nodeId, requiredLong(op, "expectedRevision"),
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "NODE", nodeId, null, null);
    }

    private Map<String, Object> createRelation(ToolCallRequest request, String kind, Map<String, Object> op) {
        StoryBibleRelation relation = storyBibleApplicationService.createRelation(request.projectId(),
                new StoryBibleCommands.CreateRelation(
                        requiredLong(op, "sourceNodeId"), requiredText(op, "relationType"),
                        requiredLong(op, "targetNodeId"), nullableText(op, "description", null),
                        text(op, "attributesJson", "{}")
                ), StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "RELATION", relation.getRelationId(), relation.getRevision(), relation);
    }

    private Map<String, Object> updateRelation(ToolCallRequest request, String kind, Map<String, Object> op) {
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

    private Map<String, Object> deleteRelation(ToolCallRequest request, String kind, Map<String, Object> op) {
        Long relationId = requiredLong(op, "relationId");
        storyBibleApplicationService.deleteRelation(request.projectId(), relationId, requiredLong(op, "expectedRevision"),
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "RELATION", relationId, null, null);
    }

    private Map<String, Object> createProgression(ToolCallRequest request, String kind, Map<String, Object> op) {
        StoryBibleProgression progression = storyBibleApplicationService.createProgression(request.projectId(),
                new StoryBibleCommands.CreateProgression(
                        requiredLong(op, "nodeId"), requiredLong(op, "anchorChapterId"),
                        nullableLong(op, "endChapterId", null), nullableLong(op, "storyEventNodeId", null),
                        requiredText(op, "patchJson"), nullableText(op, "summary", null)
                ), StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "PROGRESSION", progression.getProgressionId(), progression.getRevision(), progression);
    }

    private Map<String, Object> updateProgression(ToolCallRequest request, String kind, Map<String, Object> op) {
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

    private Map<String, Object> deleteProgression(ToolCallRequest request, String kind, Map<String, Object> op) {
        Long progressionId = requiredLong(op, "progressionId");
        storyBibleApplicationService.deleteProgression(request.projectId(), progressionId, requiredLong(op, "expectedRevision"),
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "PROGRESSION", progressionId, null, null);
    }

    private Map<String, Object> createNodeType(ToolCallRequest request, String kind, Map<String, Object> op) {
        StoryBibleNodeType type = storyBibleApplicationService.createNodeType(request.projectId(),
                new StoryBibleCommands.CreateNodeType(
                        requiredText(op, "typeCode"), requiredEnum(op, "semanticFamily", StoryBibleSemanticFamily.class),
                        requiredText(op, "displayName"), nullableText(op, "iconCode", null),
                        text(op, "fieldSchemaJson", "{}"), integer(op, "sortOrder", 0)
                ), StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "NODE_TYPE", type.getTypeId(), null, type);
    }

    private Map<String, Object> updateNodeType(ToolCallRequest request, String kind, Map<String, Object> op) {
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

    private Map<String, Object> archiveNodeType(ToolCallRequest request, String kind, Map<String, Object> op) {
        Long typeId = requiredLong(op, "typeId");
        storyBibleApplicationService.archiveNodeType(request.projectId(), typeId,
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "NODE_TYPE", typeId, null, null);
    }

    private Map<String, Object> createCategory(ToolCallRequest request, String kind, Map<String, Object> op) {
        StoryBibleCategory category = storyBibleApplicationService.createCategory(request.projectId(),
                new StoryBibleCommands.CreateCategory(nullableLong(op, "parentCategoryId", null),
                        requiredText(op, "name"), integer(op, "sortOrder", 0)),
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "CATEGORY", category.getCategoryId(), null, category);
    }

    private Map<String, Object> updateCategory(ToolCallRequest request, String kind, Map<String, Object> op) {
        Long categoryId = requiredLong(op, "categoryId");
        StoryBibleCategory existing = requireCategory(request.projectId(), categoryId);
        StoryBibleCategory category = storyBibleApplicationService.updateCategory(request.projectId(), categoryId,
                new StoryBibleCommands.UpdateCategory(
                        nullableLong(op, "parentCategoryId", existing.getParentCategoryId()),
                        text(op, "name", existing.getName()), integer(op, "sortOrder", existing.getSortOrder())
                ), StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "CATEGORY", categoryId, null, category);
    }

    private Map<String, Object> deleteCategory(ToolCallRequest request, String kind, Map<String, Object> op) {
        Long categoryId = requiredLong(op, "categoryId");
        storyBibleApplicationService.deleteCategory(request.projectId(), categoryId,
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "CATEGORY", categoryId, null, null);
    }

    private Map<String, Object> createTag(ToolCallRequest request, String kind, Map<String, Object> op) {
        StoryBibleTag tag = storyBibleApplicationService.createTag(request.projectId(),
                new StoryBibleCommands.CreateTag(requiredText(op, "name"), nullableText(op, "color", null)),
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "TAG", tag.getTagId(), null, tag);
    }

    private Map<String, Object> updateTag(ToolCallRequest request, String kind, Map<String, Object> op) {
        Long tagId = requiredLong(op, "tagId");
        StoryBibleTag existing = requireTag(request.projectId(), tagId);
        StoryBibleTag tag = storyBibleApplicationService.updateTag(request.projectId(), tagId,
                new StoryBibleCommands.UpdateTag(text(op, "name", existing.getName()),
                        nullableText(op, "color", existing.getColor())),
                StoryBibleActorType.AGENT, request.operatorId(), request.runId());
        return result(kind, "TAG", tagId, null, tag);
    }

    private Map<String, Object> deleteTag(ToolCallRequest request, String kind, Map<String, Object> op) {
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

    private String requiredText(Map<String, Object> node, String field) {
        String value = text(node, field, null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private String text(Map<String, Object> node, String field, String fallback) {
        Object value = node.get(field);
        if (value == null) return fallback;
        if (value instanceof String text) return text;
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        return "";
    }

    private String nullableText(Map<String, Object> node, String field, String fallback) {
        if (!node.containsKey(field)) return fallback;
        Object value = node.get(field);
        if (value == null) return null;
        if (value instanceof String text) return text;
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        return "";
    }

    private Long requiredLong(Map<String, Object> node, String field) {
        Long value = nullableLong(node, field, null);
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private Long optionalLong(Map<String, Object> node, String field, Long fallback) {
        if (!node.containsKey(field)) return fallback;
        Long value = parsePositiveLong(node.get(field), field);
        return value == null ? fallback : value;
    }

    private Long nullableLong(Map<String, Object> node, String field, Long fallback) {
        if (!node.containsKey(field)) return fallback;
        return parsePositiveLong(node.get(field), field);
    }

    private Long parsePositiveLong(Object value, String field) {
        if (value == null) return null;
        try {
            long parsed;
            if (value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long) {
                parsed = ((Number) value).longValue();
            } else if (value instanceof java.math.BigInteger integer) {
                parsed = integer.longValueExact();
            } else if (value instanceof String text) {
                parsed = Long.parseLong(text.trim());
            } else {
                throw new NumberFormatException();
            }
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
    }

    private Integer integer(Map<String, Object> node, String field, Integer fallback) {
        Object value = node.get(field);
        if (value == null) return fallback;
        if (!(value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long
                || value instanceof java.math.BigInteger)) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        long parsed;
        try {
            parsed = value instanceof java.math.BigInteger integer
                    ? integer.longValueExact() : ((Number) value).longValue();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return (int) parsed;
    }

    private List<String> stringList(Map<String, Object> node, String field, List<String> fallback) {
        Object value = node.get(field);
        if (value == null) return fallback;
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException(field + " must be an array");
        List<String> result = new ArrayList<>();
        values.forEach(item -> {
            if (!(item instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(field + " must contain non-blank strings");
            }
            result.add(text.trim());
        });
        return List.copyOf(result);
    }

    private List<Long> longList(Map<String, Object> node, String field, List<Long> fallback) {
        Object value = node.get(field);
        if (value == null) return fallback;
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException(field + " must be an array");
        List<Long> result = new ArrayList<>();
        values.forEach(item -> result.add(parsePositiveLong(item, field)));
        return List.copyOf(result);
    }

    private <E extends Enum<E>> E enumValue(Map<String, Object> node, String field, Class<E> type, E fallback) {
        String value = text(node, field, null);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(field + " has an unsupported value");
        }
    }

    private <E extends Enum<E>> E requiredEnum(Map<String, Object> node, String field, Class<E> type) {
        E value = enumValue(node, field, type, null);
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private String json(Object value) {
        try {
            return jsonCodec.write(value);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("failed to serialize Story Bible mutation result", ex);
        }
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null) result.put(String.valueOf(key), value);
        });
        return result;
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
