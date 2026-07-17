package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.penmate.backend.application.storybible.StoryBibleEffectiveStateResolver;
import com.penmate.backend.domain.storybible.model.StoryBibleInclusionPolicy;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.model.StoryBibleProgression;
import com.penmate.backend.domain.storybible.model.StoryBibleRelation;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StoryBibleContextResolver {
    private final StoryBibleCandidateRetriever candidateRetriever;
    private final StoryBibleSelectorGateway selectorGateway;
    private final StoryBibleRepository repository;
    private final StoryBibleEffectiveStateResolver effectiveStateResolver;

    public StoryBibleContextResolver(StoryBibleCandidateRetriever candidateRetriever,
                                     StoryBibleSelectorGateway selectorGateway,
                                     StoryBibleRepository repository,
                                     StoryBibleEffectiveStateResolver effectiveStateResolver) {
        this.candidateRetriever = candidateRetriever;
        this.selectorGateway = selectorGateway;
        this.repository = repository;
        this.effectiveStateResolver = effectiveStateResolver;
    }

    public ResolvedContext resolve(StoryBibleRouteRequest request) {
        long started = System.nanoTime();
        StoryBibleCandidateRetriever.Retrieval retrieval = candidateRetriever.retrieve(request);
        if (retrieval.storyBible() == null) {
            return new ResolvedContext(new StoryBibleRouteDecision(request.routingMode(), List.of(), List.of(),
                    List.of(), Map.of(), false, 0L, 0d, null, true,
                    List.of("STORY_BIBLE_MISSING")), List.of(), List.of());
        }
        Map<Long, StoryBibleCandidateRetriever.Candidate> candidateById = new LinkedHashMap<>();
        retrieval.candidates().stream().sorted(Comparator.comparingDouble(StoryBibleCandidateRetriever.Candidate::score).reversed())
                .forEach(candidate -> candidateById.put(candidate.nodeId(), candidate));
        List<Long> selected;
        Map<Long, String> reasons = new LinkedHashMap<>();
        StoryBibleSelectorGateway.Selection selection = new StoryBibleSelectorGateway.Selection(List.of(), Map.of());
        boolean selectorUsed = request.routingMode() != StoryBibleRoutingMode.RETRIEVAL;
        if (!selectorUsed) {
            selected = new ArrayList<>(candidateById.keySet());
            candidateById.values().forEach(candidate -> reasons.put(candidate.nodeId(), String.join(",", candidate.reasons())));
        } else {
            List<StoryBibleRouteRequest.CatalogEntry> selectorCatalog = request.routingMode() == StoryBibleRoutingMode.LLM_SELECTOR
                    ? request.epochCatalog()
                    : request.epochCatalog().stream().filter(item -> candidateById.containsKey(item.nodeId())).toList();
            selection = selectorGateway.select(new StoryBibleSelectorGateway.SelectorRequest(
                    request.routingMode(), request.userMessage(), selectorCatalog, request.workingSetNodeIds(),
                    request.conversationWindow()),
                    request.selectorExecutionConfig());
            selected = new ArrayList<>(selection.nodeIds());
            reasons.putAll(selection.reasons());
            candidateById.values().stream()
                    .filter(candidate -> candidate.reasons().contains("always_include") && !selected.contains(candidate.nodeId()))
                    .forEach(candidate -> {
                        selected.add(candidate.nodeId());
                        reasons.put(candidate.nodeId(), "always_include");
                    });
        }

        List<StoryBibleRelation> relations = repository.findRelations(retrieval.storyBible().getStoryBibleId(), selected);
        Set<Long> epochIds = request.epochCatalog().stream().map(StoryBibleRouteRequest.CatalogEntry::nodeId)
                .collect(java.util.stream.Collectors.toSet());
        LinkedHashSet<Long> expanded = new LinkedHashSet<>(selected);
        request.workingSetNodeIds().stream().filter(epochIds::contains).forEach(expanded::add);
        if (selectorUsed) {
            selection.relationExpansionNodeIds().stream().filter(epochIds::contains).forEach(expanded::add);
        } else {
            for (StoryBibleRelation relation : relations) {
                if (expanded.contains(relation.getSourceNodeId()) || expanded.contains(relation.getTargetNodeId())) {
                    expanded.add(relation.getSourceNodeId());
                    expanded.add(relation.getTargetNodeId());
                }
            }
        }
        List<StoryBibleNode> nodes = expanded.isEmpty() ? List.of()
                : repository.findNodesByIds(retrieval.storyBible().getStoryBibleId(), List.copyOf(expanded));
        Map<Long, StoryBibleNodeType> types = new HashMap<>();
        for (StoryBibleNodeType type : repository.findNodeTypes(retrieval.storyBible().getStoryBibleId())) types.put(type.getTypeId(), type);
        Map<Long, List<StoryBibleProgression>> progressionsByNode = expanded.isEmpty() ? Map.of() : repository
                .findProgressions(retrieval.storyBible().getStoryBibleId(), List.copyOf(expanded)).stream()
                .collect(java.util.stream.Collectors.groupingBy(StoryBibleProgression::getNodeId));
        List<RenderedNode> rendered = new ArrayList<>();
        List<String> missing = new ArrayList<>(selection.missingContextFlags());
        for (StoryBibleNode node : nodes) {
            StoryBibleNodeType type = types.get(node.getTypeId());
            if (type == null) {
                missing.add("NODE_TYPE_MISSING:" + node.getNodeId());
                continue;
            }
            var effective = effectiveStateResolver.resolve(request.projectId(), request.chapterId(), node, type,
                    progressionsByNode.getOrDefault(node.getNodeId(), List.of()));
            if (!effective.complete()) missing.add("EFFECTIVE_STATE_INCOMPLETE:" + node.getNodeId());
            rendered.add(new RenderedNode(node.getNodeId(), node.getTitle(), type.getTypeCode(), effective.state(),
                    effective.appliedProgressionIds(), effective.complete()));
        }
        long latency = selectorUsed ? (System.nanoTime() - started) / 1_000_000L : 0L;
        StoryBibleRouteDecision decision = new StoryBibleRouteDecision(request.routingMode(), selection.intentTags(),
                List.copyOf(selected), selection.relationExpansionNodeIds(), reasons, selectorUsed, latency,
                selection.confidence(), selection.tokenUsage(), retrieval.semanticUnavailable(), missing);
        return new ResolvedContext(decision, List.copyOf(rendered), relations);
    }

    public record RenderedNode(Long nodeId, String title, String typeCode, JsonNode effectiveState,
                               List<Long> appliedProgressionIds, boolean complete) {
    }
    public record ResolvedContext(StoryBibleRouteDecision decision, List<RenderedNode> nodes,
                                  List<StoryBibleRelation> relations) {
    }
}
