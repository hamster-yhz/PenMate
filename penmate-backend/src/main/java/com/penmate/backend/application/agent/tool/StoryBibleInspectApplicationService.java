package com.penmate.backend.application.agent.tool;

import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.storybible.StoryBibleApplicationService;
import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.model.StoryBibleSemanticFamily;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StoryBibleInspectApplicationService {

    private final StoryBibleApplicationService storyBible;
    private final JsonCodec jsonCodec;

    public StoryBibleInspectApplicationService(StoryBibleApplicationService storyBible, JsonCodec jsonCodec) {
        this.storyBible = storyBible;
        this.jsonCodec = jsonCodec;
    }

    public ToolCallResult execute(AuthorizedAgentRunContext context, ToolCallRequest request) {
        try {
            InspectArgs args = jsonCodec.read(request.toolArgsJson(), InspectArgs.class);
            Object result = switch (args.operation()) {
                case "overview" -> overview(context.projectId());
                case "types" -> types(context.projectId(), args.typeCode(), args.semanticFamily());
                case "nodes" -> nodes(context.projectId(), args);
                case "node" -> inspectNode(context, requireNodeId(args.nodeId()));
                default -> throw new IllegalArgumentException("unsupported operation: " + args.operation());
            };
            return ToolCallResult.success(jsonCodec.write(result));
        } catch (RuntimeException exception) {
            return ToolCallResult.failed("STORY_BIBLE_INSPECT_FAILED", message(exception));
        }
    }

    private Map<String, Object> overview(Long projectId) {
        var root = storyBible.get(projectId);
        List<StoryBibleNodeType> types = storyBible.listNodeTypes(projectId);
        List<StoryBibleNode> nodes = storyBible.listNodes(projectId, null, null, null);
        Map<Long, StoryBibleNodeType> typesById = types.stream()
                .collect(Collectors.toMap(StoryBibleNodeType::getTypeId, Function.identity()));
        StoryBibleNode core = nodes.stream()
                .filter(node -> typesById.get(node.getTypeId()) != null)
                .filter(node -> "STORY_CORE".equals(typesById.get(node.getTypeId()).getTypeCode()))
                .findFirst().orElse(null);
        List<StoryBibleNode> activeNodes = nodes.stream()
                .filter(node -> node.getCanonStatus() != StoryBibleCanonStatus.ARCHIVED).toList();
        List<String> structuralIssues = new java.util.ArrayList<>();
        nodes.stream().filter(node -> !typesById.containsKey(node.getTypeId()))
                .forEach(node -> structuralIssues.add("NODE_TYPE_MISSING:" + node.getNodeId()));
        if (core == null) structuralIssues.add("STORY_CORE_MISSING");
        List<String> missingCoreFields = core == null ? List.of("storyCore")
                : missingRequiredFields(core, typesById.get(core.getTypeId()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contentRevision", root.getContentRevision());
        result.put("catalogTypeCount", types.size());
        result.put("activeNodeCount", activeNodes.size());
        result.put("archivedNodeCount", nodes.size() - activeNodes.size());
        result.put("countsByType", activeNodes.stream().filter(node -> typesById.containsKey(node.getTypeId()))
                .collect(Collectors.groupingBy(node -> typesById.get(node.getTypeId()).getTypeCode(),
                        java.util.TreeMap::new, Collectors.counting())));
        result.put("storyCore", core == null ? null : renderNode(core, typesById.get(core.getTypeId())));
        result.put("missingRequiredStoryCoreFields", missingCoreFields);
        result.put("structuralIssues", structuralIssues);
        result.put("latestChanges", storyBible.recentChanges(projectId, 10));
        return result;
    }

    private List<String> missingRequiredFields(StoryBibleNode node, StoryBibleNodeType type) {
        if (type == null) return List.of("nodeType");
        Map<String, Object> schema = jsonCodec.readObject(type.getFieldSchemaJson());
        Object rawRequired = schema.get("required");
        if (!(rawRequired instanceof List<?> required)) return List.of();
        Map<String, Object> attributes = jsonCodec.readObject(node.getAttributesJson());
        return required.stream().map(String::valueOf)
                .filter(field -> !attributes.containsKey(field) || attributes.get(field) == null
                        || attributes.get(field) instanceof String text && text.isBlank())
                .toList();
    }

    private Map<String, Object> types(Long projectId, String requestedTypeCode,
                                      StoryBibleSemanticFamily requestedFamily) {
        List<StoryBibleNodeType> allTypes = storyBible.listNodeTypes(projectId);
        TypeFilter filter = resolveTypeFilter(allTypes, requestedTypeCode, requestedFamily, false);
        List<Map<String, Object>> renderedTypes = filter.types().stream().map(type -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("typeId", String.valueOf(type.getTypeId()));
            value.put("typeCode", type.getTypeCode());
            value.put("semanticFamily", type.getSemanticFamily());
            value.put("displayName", type.getDisplayName());
            value.put("system", type.getSystem());
            value.put("sortOrder", type.getSortOrder());
            value.put("fieldSchema", jsonCodec.readObject(type.getFieldSchemaJson()));
            value.put("nodeCount", storyBible.listNodes(projectId, type.getTypeId(), null, null).size());
            return value;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("types", renderedTypes);
        result.put("categories", storyBible.listCategories(projectId));
        result.put("tags", storyBible.listTags(projectId));
        result.put("resolvedFilter", filter.resolvedFilter());
        if (filter.warning() != null) result.put("warning", filter.warning());
        result.put("availableTypes", availableTypes(allTypes));
        return result;
    }

    private Map<String, Object> nodes(Long projectId, InspectArgs args) {
        List<StoryBibleNodeType> allTypes = storyBible.listNodeTypes(projectId);
        TypeFilter filter = resolveTypeFilter(allTypes, args.typeCode(), args.semanticFamily(), true);
        Map<Long, StoryBibleNodeType> typesById = allTypes.stream()
                .collect(Collectors.toMap(StoryBibleNodeType::getTypeId, Function.identity()));
        java.util.Set<Long> allowedTypeIds = filter.types().stream()
                .map(StoryBibleNodeType::getTypeId).collect(Collectors.toSet());
        List<StoryBibleNode> matches = storyBible.listNodes(
                        projectId, null, args.canonStatus(), normalizeQuery(args.query())).stream()
                .filter(node -> allowedTypeIds.contains(node.getTypeId()))
                .sorted(Comparator
                        .comparing((StoryBibleNode node) -> {
                            StoryBibleNodeType type = typesById.get(node.getTypeId());
                            return type == null || type.getSortOrder() == null
                                    ? Integer.MAX_VALUE : type.getSortOrder();
                        })
                        .thenComparing(StoryBibleNode::getTitle, Comparator.nullsLast(String::compareTo))
                        .thenComparing(StoryBibleNode::getNodeId))
                .toList();
        int offset = args.offset() == null ? 0 : Math.max(0, args.offset());
        int limit = args.limit() == null ? 50 : Math.max(1, Math.min(100, args.limit()));
        int from = Math.min(offset, matches.size());
        int to = Math.min(matches.size(), from + limit);
        List<Map<String, Object>> items = matches.subList(from, to).stream().map(node -> {
            StoryBibleNodeType type = typesById.get(node.getTypeId());
            Map<String, Object> value = renderNodeSummary(node, type);
            value.put("semanticFamily", type == null ? "UNKNOWN" : type.getSemanticFamily());
            value.put("summary", node.getSummary());
            value.put("inclusionPolicy", node.getInclusionPolicy());
            value.put("canonStatus", node.getCanonStatus());
            return value;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", matches.size());
        result.put("offset", from);
        result.put("limit", limit);
        result.put("hasMore", to < matches.size());
        result.put("nextOffset", to < matches.size() ? to : null);
        result.put("resolvedFilter", filter.resolvedFilter());
        if (filter.warning() != null) result.put("warning", filter.warning());
        result.put("availableTypes", availableTypes(allTypes));
        return result;
    }

    private TypeFilter resolveTypeFilter(List<StoryBibleNodeType> allTypes, String requestedTypeCode,
                                         StoryBibleSemanticFamily requestedFamily,
                                         boolean ignoreUnknownTypeCode) {
        List<StoryBibleNodeType> filtered = allTypes;
        String warning = null;
        String resolvedFilter = null;
        if (requestedTypeCode != null && !requestedTypeCode.isBlank()) {
            String normalized = requestedTypeCode.trim();
            filtered = allTypes.stream()
                    .filter(type -> type.getTypeCode().equalsIgnoreCase(normalized)).toList();
            if (filtered.isEmpty()) {
                StoryBibleSemanticFamily family = parseFamily(normalized);
                if (family != null) {
                    filtered = allTypes.stream().filter(type -> type.getSemanticFamily() == family).toList();
                    resolvedFilter = "semanticFamily";
                    warning = "typeCode '" + normalized + "' is a semantic family; matched semanticFamily instead";
                } else if (requestedFamily != null) {
                    filtered = allTypes.stream().filter(type -> type.getSemanticFamily() == requestedFamily).toList();
                    resolvedFilter = "semanticFamily";
                    warning = "Unknown typeCode '" + normalized
                            + "' was ignored; matched the explicit semanticFamily instead.";
                } else {
                    filtered = ignoreUnknownTypeCode ? allTypes : List.of();
                    warning = "Unknown typeCode '" + normalized
                            + "'. Use one of availableTypes.typeCode or omit the filter.";
                }
            } else {
                resolvedFilter = "typeCode";
            }
        } else if (requestedFamily != null) {
            filtered = allTypes.stream().filter(type -> type.getSemanticFamily() == requestedFamily).toList();
            resolvedFilter = "semanticFamily";
        }
        return new TypeFilter(filtered, resolvedFilter, warning);
    }

    private List<Map<String, Object>> availableTypes(List<StoryBibleNodeType> types) {
        return types.stream().map(type -> Map.<String, Object>of(
                "typeCode", type.getTypeCode(),
                "semanticFamily", type.getSemanticFamily(),
                "displayName", type.getDisplayName()
        )).toList();
    }

    private String normalizeQuery(String query) {
        return query == null || query.isBlank() ? null : query.trim();
    }

    private StoryBibleSemanticFamily parseFamily(String value) {
        try {
            return StoryBibleSemanticFamily.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Map<String, Object> inspectNode(AuthorizedAgentRunContext context, Long nodeId) {
        Long projectId = context.projectId();
        var details = storyBible.getNodeDetails(projectId, nodeId);
        StoryBibleNode node = details.node();
        StoryBibleNodeType type = storyBible.listNodeTypes(projectId).stream()
                .filter(candidate -> candidate.getTypeId().equals(node.getTypeId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Story Bible node type not found"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(renderNode(node, type));
        result.put("aliases", details.aliases().stream().map(alias -> alias.getAlias()).toList());
        result.put("categoryIds", details.categoryIds().stream().map(String::valueOf).toList());
        result.put("tagIds", details.tagIds().stream().map(String::valueOf).toList());
        result.put("relations", storyBible.listRelations(projectId, List.of(nodeId)).stream().map(relation -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("relationId", String.valueOf(relation.getRelationId()));
            value.put("revision", relation.getRevision());
            value.put("sourceNodeId", String.valueOf(relation.getSourceNodeId()));
            value.put("relationType", relation.getRelationType());
            value.put("targetNodeId", String.valueOf(relation.getTargetNodeId()));
            value.put("description", relation.getDescription());
            value.put("attributes", jsonCodec.readObject(relation.getAttributesJson()));
            return value;
        }).toList());
        result.put("progressions", storyBible.listProgressions(projectId, List.of(nodeId)).stream().map(progression -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("progressionId", String.valueOf(progression.getProgressionId()));
            value.put("revision", progression.getRevision());
            value.put("anchorChapterId", String.valueOf(progression.getAnchorChapterId()));
            value.put("endChapterId", progression.getEndChapterId() == null ? null : String.valueOf(progression.getEndChapterId()));
            value.put("storyEventNodeId", progression.getStoryEventNodeId() == null ? null : String.valueOf(progression.getStoryEventNodeId()));
            value.put("patch", jsonCodec.read(progression.getPatchJson()));
            value.put("summary", progression.getSummary());
            return value;
        }).toList());
        if (context.input().chapterId() != null) {
            result.put("effectiveState", storyBible.getEffectiveState(projectId, nodeId, context.input().chapterId()));
        }
        return result;
    }

    private Map<String, Object> renderNode(StoryBibleNode node, StoryBibleNodeType type) {
        Map<String, Object> value = renderNodeSummary(node, type);
        value.put("summary", node.getSummary());
        value.put("bodyMarkdown", node.getBodyMarkdown());
        value.put("attributes", jsonCodec.readObject(node.getAttributesJson()));
        value.put("inclusionPolicy", node.getInclusionPolicy());
        value.put("canonStatus", node.getCanonStatus());
        value.put("fieldSchema", jsonCodec.readObject(type.getFieldSchemaJson()));
        return value;
    }

    private Map<String, Object> renderNodeSummary(StoryBibleNode node, StoryBibleNodeType type) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("nodeId", String.valueOf(node.getNodeId()));
        value.put("revision", node.getRevision());
        value.put("title", node.getTitle());
        value.put("typeId", type == null ? null : String.valueOf(type.getTypeId()));
        value.put("typeCode", type == null ? "UNKNOWN" : type.getTypeCode());
        return value;
    }

    private static Long requireNodeId(Long nodeId) {
        if (nodeId == null || nodeId <= 0) throw new IllegalArgumentException("nodeId is required for node inspection");
        return nodeId;
    }

    private static String message(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    private record TypeFilter(List<StoryBibleNodeType> types, String resolvedFilter, String warning) { }

    public record InspectArgs(String operation, Long nodeId, String typeCode,
                              StoryBibleSemanticFamily semanticFamily, String query,
                              StoryBibleCanonStatus canonStatus, Integer offset, Integer limit) {
        public InspectArgs {
            operation = operation == null ? "" : operation.trim().toLowerCase();
        }
    }
}
