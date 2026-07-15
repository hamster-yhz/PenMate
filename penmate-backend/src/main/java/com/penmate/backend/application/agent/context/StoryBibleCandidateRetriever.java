package com.penmate.backend.application.agent.context;

import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleAlias;
import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.domain.storybible.model.StoryBibleInclusionPolicy;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StoryBibleCandidateRetriever {
    private static final int LIMIT = 40;

    private final StoryBibleRepository repository;
    private final StoryBibleSemanticRetriever semanticRetriever;

    public StoryBibleCandidateRetriever(StoryBibleRepository repository, StoryBibleSemanticRetriever semanticRetriever) {
        this.repository = repository;
        this.semanticRetriever = semanticRetriever;
    }

    public Retrieval retrieve(StoryBibleRouteRequest request) {
        StoryBible root = repository.findByProjectId(request.projectId());
        if (root == null) return new Retrieval(null, List.of(), true);
        if (request.storyBibleRevision() != null && !request.storyBibleRevision().equals(root.getContentRevision())) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict(
                    "Story Bible changed after Context Epoch binding");
        }
        Map<Long, Candidate> merged = new LinkedHashMap<>();
        repository.findAlwaysIncludeNodes(root.getStoryBibleId()).stream()
                .forEach(node -> merge(merged, node.getNodeId(), 100d, "always_include"));

        for (String entity : request.userMentionedEntities()) {
            if (entity == null || entity.isBlank()) continue;
            for (StoryBibleAlias alias : repository.findByNormalizedAlias(root.getStoryBibleId(), normalize(entity))) {
                merge(merged, alias.getNodeId(), 90d, "exact_alias:" + entity.trim());
            }
        }
        List<String> terms = terms(request.userMessage());
        if (!terms.isEmpty()) {
            for (StoryBibleNode node : repository.searchNodesLexically(root.getStoryBibleId(), terms, LIMIT)) {
                merge(merged, node.getNodeId(), 50d, "lexical");
            }
        }
        StoryBibleSemanticRetriever.SemanticResult semantic = semanticRetriever.retrieve(
                root.getStoryBibleId(), request.userMessage(), LIMIT);
        for (Candidate candidate : semantic.candidates()) {
            for (String reason : candidate.reasons()) merge(merged, candidate.nodeId(), candidate.score(), reason);
        }
        java.util.Set<Long> epochNodeIds = request.epochCatalog().stream()
                .map(StoryBibleRouteRequest.CatalogEntry::nodeId).collect(java.util.stream.Collectors.toSet());
        List<Candidate> candidates = merged.values().stream()
                .filter(candidate -> epochNodeIds.contains(candidate.nodeId()))
                .toList();
        return new Retrieval(root, candidates, !semantic.available());
    }

    private void merge(Map<Long, Candidate> merged, Long nodeId, double score, String reason) {
        merged.merge(nodeId, new Candidate(nodeId, score, List.of(reason)), (left, right) -> {
            LinkedHashSet<String> reasons = new LinkedHashSet<>(left.reasons());
            reasons.addAll(right.reasons());
            return new Candidate(nodeId, Math.max(left.score(), right.score()), List.copyOf(reasons));
        });
    }

    private List<String> terms(String message) {
        if (message == null || message.isBlank()) return List.of();
        String[] raw = message.trim().split("[\\s,，。！？!?、:：;；]+", 12);
        List<String> result = new ArrayList<>();
        for (String value : raw) if (value.length() >= 2) result.add(value);
        return result.stream().limit(10).toList();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    public record Candidate(Long nodeId, double score, List<String> reasons) {
        public Candidate(Long nodeId, double score, String reason) { this(nodeId, score, List.of(reason)); }
    }
    public record Retrieval(StoryBible storyBible, List<Candidate> candidates, boolean semanticUnavailable) {
    }
}
