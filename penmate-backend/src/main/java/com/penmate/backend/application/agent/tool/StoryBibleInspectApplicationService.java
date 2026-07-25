package com.penmate.backend.application.agent.tool;

import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.storybible.StoryBibleApplicationService;
import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
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
                case "readiness" -> readiness(context.projectId());
                case "catalog" -> catalog(context.projectId(), args.typeCode());
                case "node" -> inspectNode(context, requireNodeId(args.nodeId()));
                default -> throw new IllegalArgumentException("unsupported operation: " + args.operation());
            };
            return ToolCallResult.success(jsonCodec.write(result));
        } catch (RuntimeException exception) {
            return ToolCallResult.failed("STORY_BIBLE_INSPECT_FAILED", message(exception));
        }
    }

    private Map<String, Object> readiness(Long projectId) {
        var root = storyBible.get(projectId);
        List<StoryBibleNodeType> types = storyBible.listNodeTypes(projectId);
        List<StoryBibleNode> nodes = storyBible.listNodes(projectId, null, null, null);
        Map<Long, StoryBibleNodeType> typesById = types.stream()
                .collect(Collectors.toMap(StoryBibleNodeType::getTypeId, Function.identity()));
        StoryBibleNode core = nodes.stream()
                .filter(node -> "STORY_CORE".equals(typesById.get(node.getTypeId()).getTypeCode()))
                .findFirst().orElse(null);
        boolean coreHasContent = core != null && (hasText(core.getSummary()) || hasText(core.getBodyMarkdown())
                || !jsonCodec.readObject(core.getAttributesJson()).isEmpty());
        long substantiveNodes = nodes.stream()
                .filter(node -> node.getCanonStatus() != StoryBibleCanonStatus.ARCHIVED)
                .filter(node -> core == null || !Objects.equals(node.getNodeId(), core.getNodeId()))
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contentRevision", root.getContentRevision());
        result.put("catalogTypeCount", types.size());
        result.put("activeNodeCount", substantiveNodes + (core == null ? 0 : 1));
        result.put("contentReady", coreHasContent || substantiveNodes > 0);
        result.put("storyCore", core == null ? null : renderNodeSummary(core, typesById.get(core.getTypeId())));
        result.put("nextAction", coreHasContent || substantiveNodes > 0
                ? "inspect_existing_canon_before_changes"
                : "analyze_sources_and_present_initialization_plan_for_user_confirmation");
        return result;
    }

    private Map<String, Object> catalog(Long projectId, String requestedTypeCode) {
        List<StoryBibleNodeType> types = storyBible.listNodeTypes(projectId).stream()
                .filter(type -> requestedTypeCode == null || requestedTypeCode.isBlank()
                        || type.getTypeCode().equalsIgnoreCase(requestedTypeCode.trim()))
                .toList();
        if (types.isEmpty() && requestedTypeCode != null && !requestedTypeCode.isBlank()) {
            throw new IllegalArgumentException("Story Bible node type not found: " + requestedTypeCode);
        }
        List<Map<String, Object>> renderedTypes = types.stream().map(type -> {
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
        return Map.of(
                "types", renderedTypes,
                "categories", storyBible.listCategories(projectId),
                "tags", storyBible.listTags(projectId)
        );
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
        value.put("typeId", String.valueOf(type.getTypeId()));
        value.put("typeCode", type.getTypeCode());
        return value;
    }

    private static Long requireNodeId(Long nodeId) {
        if (nodeId == null || nodeId <= 0) throw new IllegalArgumentException("nodeId is required for node inspection");
        return nodeId;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String message(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    public record InspectArgs(String operation, Long nodeId, String typeCode) {
        public InspectArgs {
            operation = operation == null ? "" : operation.trim().toLowerCase();
        }
    }
}
