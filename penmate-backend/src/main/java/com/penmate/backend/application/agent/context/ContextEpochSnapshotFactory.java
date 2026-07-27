package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleAlias;
import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.domain.storybible.model.StoryBibleInclusionPolicy;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.model.StoryBibleProgression;
import com.penmate.backend.domain.storybible.model.StoryBibleRelation;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import com.penmate.backend.application.storybible.StoryBibleEffectiveStateResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ContextEpochSnapshotFactory {
    private final StoryBibleRepository storyBibles;
    private final NovelGateway novels;
    private final StoryBibleEffectiveStateResolver effectiveStates;
    private final JsonCodec jsonCodec;

    public ContextEpochSnapshotFactory(StoryBibleRepository storyBibles, NovelGateway novels,
                                       StoryBibleEffectiveStateResolver effectiveStates, JsonCodec jsonCodec) {
        this.storyBibles = storyBibles;
        this.novels = novels;
        this.effectiveStates = effectiveStates;
        this.jsonCodec = jsonCodec;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ContextEpochSnapshotCodec.Snapshot create(Long projectId, Long activeChapterId,
                                                     StoryBibleRoutingMode routingMode) {
        StoryBible root = storyBibles.findByProjectId(projectId);
        if (root == null) throw BusinessException.notFound("Story Bible not found");
        NovelProject project = novels.findProjectById(projectId);
        if (project == null) throw BusinessException.notFound("Novel project not found");
        NovelChapter activeChapter = activeChapterId == null ? null
                : novels.findChapterByIdAndProjectId(projectId, activeChapterId);
        long chapterRevision = activeChapter == null || activeChapter.getContentRevision() == null
                ? 0L : activeChapter.getContentRevision();
        boolean preparesContext = routingMode.preparesContext();
        Map<Long, StoryBibleNodeType> types = new HashMap<>();
        for (StoryBibleNodeType type : storyBibles.findNodeTypes(root.getStoryBibleId())) types.put(type.getTypeId(), type);
        List<StoryBibleNode> nodes = storyBibles.findNodes(
                root.getStoryBibleId(), null, StoryBibleCanonStatus.CANON.name(), null).stream()
                .sorted(Comparator.comparing((StoryBibleNode node) -> {
                    StoryBibleNodeType type = types.get(node.getTypeId());
                    return type == null || type.getSortOrder() == null ? Integer.MAX_VALUE : type.getSortOrder();
                }).thenComparing(StoryBibleNode::getTitle).thenComparing(StoryBibleNode::getNodeId))
                .toList();
        List<Long> nodeIds = nodes.stream().map(StoryBibleNode::getNodeId).toList();
        Map<Long, List<StoryBibleAlias>> aliases = storyBibles.findAliasesByNodeIds(root.getStoryBibleId(), nodeIds)
                .stream().collect(Collectors.groupingBy(StoryBibleAlias::getNodeId));
        List<StoryBibleRelation> relations = nodeIds.isEmpty() ? List.of()
                : storyBibles.findRelations(root.getStoryBibleId(), nodeIds);
        Map<Long, List<StoryBibleProgression>> progressions = (!preparesContext || nodeIds.isEmpty()
                ? List.<StoryBibleProgression>of()
                : storyBibles.findProgressions(root.getStoryBibleId(), nodeIds))
                .stream().collect(Collectors.groupingBy(StoryBibleProgression::getNodeId));
        Map<Long, StoryBibleNode> nodesById = nodes.stream()
                .collect(Collectors.toMap(StoryBibleNode::getNodeId, node -> node));
        Map<Long, StoryBibleEffectiveStateResolver.EffectiveState> stateByNode = new HashMap<>();
        for (StoryBibleNode node : preparesContext ? nodes : List.<StoryBibleNode>of()) {
            StoryBibleNodeType type = types.get(node.getTypeId());
            if (type == null) continue;
            var state = activeChapterId == null
                    ? effectiveStates.resolveBase(node)
                    : effectiveStates.resolve(projectId, activeChapterId, node, type,
                    progressions.getOrDefault(node.getNodeId(), List.of()));
            stateByNode.put(node.getNodeId(), state);
        }
        List<ContextEpochSnapshotCodec.CoreNode> core = (preparesContext ? nodes : List.<StoryBibleNode>of()).stream()
                .filter(node -> node.getInclusionPolicy() == StoryBibleInclusionPolicy.ALWAYS_INCLUDE)
                .map(node -> toCoreNode(node, types.get(node.getTypeId()), stateByNode.get(node.getNodeId())))
                .filter(Objects::nonNull)
                .toList();
        List<StoryBibleRouteRequest.CatalogEntry> catalog = nodes.stream().map(node -> {
            StoryBibleNodeType type = types.get(node.getTypeId());
            var state = stateByNode.get(node.getNodeId());
            return new StoryBibleRouteRequest.CatalogEntry(node.getNodeId(),
                    type == null || type.getSemanticFamily() == null ? "UNKNOWN" : type.getSemanticFamily().name(),
                    type == null ? "UNKNOWN" : type.getTypeCode(), node.getTitle(),
                    aliases.getOrDefault(node.getNodeId(), List.of()).stream()
                            .map(StoryBibleAlias::getAlias).filter(Objects::nonNull).sorted().toList(),
                    node.getSummary(), keyRelations(node.getNodeId(), relations, nodesById),
                    currentStateSummary(state),
                    node.getInclusionPolicy().name(), node.getCanonStatus().name());
        }).toList();
        return new ContextEpochSnapshotCodec.Snapshot(2, projectId, root.getStoryBibleId(), root.getContentRevision(),
                project.getStructureRevision() == null ? 0L : project.getStructureRevision(), activeChapterId,
                chapterRevision, core, catalog);
    }

    private ContextEpochSnapshotCodec.CoreNode toCoreNode(
            StoryBibleNode node,
            StoryBibleNodeType type,
            StoryBibleEffectiveStateResolver.EffectiveState state
    ) {
        if (type == null || state == null) return null;
        return new ContextEpochSnapshotCodec.CoreNode(node.getNodeId(), node.getTypeId(), type.getTypeCode(),
                type.getSemanticFamily() == null ? "UNKNOWN" : type.getSemanticFamily().name(),
                node.getTitle(), state.state(), state.appliedProgressionIds(),
                stateFlags(state));
    }

    private List<StoryBibleRouteRequest.CatalogRelation> keyRelations(
            Long nodeId,
            List<StoryBibleRelation> relations,
            Map<Long, StoryBibleNode> nodesById
    ) {
        List<StoryBibleRouteRequest.CatalogRelation> result = new ArrayList<>();
        for (StoryBibleRelation relation : relations) {
            boolean outgoing = Objects.equals(nodeId, relation.getSourceNodeId());
            boolean incoming = Objects.equals(nodeId, relation.getTargetNodeId());
            if (!outgoing && !incoming) continue;
            Long otherId = outgoing ? relation.getTargetNodeId() : relation.getSourceNodeId();
            StoryBibleNode other = nodesById.get(otherId);
            if (other == null) continue;
            result.add(new StoryBibleRouteRequest.CatalogRelation(outgoing ? "OUT" : "IN",
                    relation.getRelationType(), otherId, other.getTitle()));
        }
        return result.stream().sorted(Comparator
                .comparing(StoryBibleRouteRequest.CatalogRelation::relationType, Comparator.nullsFirst(String::compareTo))
                .thenComparing(StoryBibleRouteRequest.CatalogRelation::direction)
                .thenComparing(StoryBibleRouteRequest.CatalogRelation::otherNodeId)).toList();
    }

    private String currentStateSummary(StoryBibleEffectiveStateResolver.EffectiveState state) {
        if (state == null) return "";
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("title", state.state().get("title"));
        summary.put("summary", state.state().get("summary"));
        summary.put("attributes", state.state().get("attributes"));
        summary.put("appliedProgressionIds", state.appliedProgressionIds());
        summary.put("stateFlags", stateFlags(state));
        try {
            return jsonCodec.write(summary);
        } catch (RuntimeException ex) {
            throw BusinessException.of("Failed to serialize Story Bible current state summary");
        }
    }

    private List<String> stateFlags(StoryBibleEffectiveStateResolver.EffectiveState state) {
        List<String> flags = new ArrayList<>();
        state.unresolvedAnchors().forEach(anchor -> flags.add(anchor.code() + ":" + anchor.role()));
        state.conflicts().forEach(conflict -> flags.add(conflict.code()));
        return List.copyOf(flags);
    }
}
