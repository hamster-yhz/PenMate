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
        if (root == null) return new Retrieval(null, List.of(), StoryBibleRetrievalTrace.EMPTY);
        if (request.storyBibleRevision() != null && !request.storyBibleRevision().equals(root.getContentRevision())) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict(
                    "Story Bible changed after Context Epoch binding");
        }
        Map<Long, Candidate> merged = new LinkedHashMap<>();
        LinkedHashSet<Long> alwaysIncludeIds = new LinkedHashSet<>();
        LinkedHashSet<Long> exactAliasIds = new LinkedHashSet<>();
        LinkedHashSet<Long> lexicalIds = new LinkedHashSet<>();
        LinkedHashSet<Long> semanticIds = new LinkedHashSet<>();
        if (request.routingMode() != StoryBibleRoutingMode.AGENT_DRIVEN) {
            repository.findAlwaysIncludeNodes(root.getStoryBibleId()).forEach(node -> {
                alwaysIncludeIds.add(node.getNodeId());
                merge(merged, node.getNodeId(), 100d, "always_include");
            });
        }

        for (String entity : request.userMentionedEntities()) {
            if (entity == null || entity.isBlank()) continue;
            for (StoryBibleAlias alias : repository.findByNormalizedAlias(root.getStoryBibleId(), normalize(entity))) {
                exactAliasIds.add(alias.getNodeId());
                merge(merged, alias.getNodeId(), 90d, "exact_alias:" + entity.trim());
            }
        }
        String normalizedMessage = normalize(request.userMessage());
        for (StoryBibleRouteRequest.CatalogEntry entry : request.epochCatalog()) {
            for (String alias : entry.aliases()) {
                if (alias == null || alias.isBlank()) continue;
                String normalizedAlias = normalize(alias);
                if (!containsMention(normalizedMessage, normalizedAlias)) continue;
                exactAliasIds.add(entry.nodeId());
                merge(merged, entry.nodeId(), 90d, "exact_alias:" + alias.trim());
            }
        }
        List<String> terms = terms(request.userMessage());
        if (!terms.isEmpty()) {
            for (StoryBibleNode node : repository.searchNodesLexically(root.getStoryBibleId(), terms, LIMIT)) {
                lexicalIds.add(node.getNodeId());
                merge(merged, node.getNodeId(), 50d, "lexical");
            }
        }
        StoryBibleSemanticRetriever.SemanticResult semantic = request.routingMode().usesRetrieval()
                ? semanticRetriever.retrieve(request.projectId(), root.getStoryBibleId(), request.userMessage(), LIMIT)
                : new StoryBibleSemanticRetriever.SemanticResult(false, List.of());
        for (Candidate candidate : semantic.candidates()) {
            semanticIds.add(candidate.nodeId());
            for (String reason : candidate.reasons()) merge(merged, candidate.nodeId(), candidate.score(), reason);
        }
        java.util.Set<Long> epochNodeIds = request.epochCatalog().stream()
                .map(StoryBibleRouteRequest.CatalogEntry::nodeId).collect(java.util.stream.Collectors.toSet());
        List<Candidate> candidates = merged.values().stream()
                .filter(candidate -> epochNodeIds.isEmpty() || epochNodeIds.contains(candidate.nodeId()))
                .toList();
        alwaysIncludeIds.retainAll(epochNodeIds);
        exactAliasIds.retainAll(epochNodeIds);
        lexicalIds.retainAll(epochNodeIds);
        semanticIds.retainAll(epochNodeIds);
        StoryBibleRetrievalTrace trace = new StoryBibleRetrievalTrace(
                semantic.available(), alwaysIncludeIds.size(), exactAliasIds.size(), lexicalIds.size(),
                semanticIds.size(), candidates.size(), candidates.stream()
                .map(candidate -> new StoryBibleRetrievalTrace.Candidate(
                        candidate.nodeId(), candidate.score(), candidate.reasons()))
                .toList());
        return new Retrieval(root, candidates, trace);
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

    private boolean containsMention(String message, String mention) {
        if (message.isBlank() || mention.isBlank()) return false;
        if (mention.codePoints().anyMatch(codePoint -> codePoint > 127)) {
            return message.contains(mention);
        }
        int from = 0;
        while (from <= message.length() - mention.length()) {
            int index = message.indexOf(mention, from);
            if (index < 0) return false;
            int end = index + mention.length();
            boolean leftBoundary = index == 0 || !isWordCharacter(message.charAt(index - 1));
            boolean rightBoundary = end == message.length() || !isWordCharacter(message.charAt(end));
            if (leftBoundary && rightBoundary) return true;
            from = index + 1;
        }
        return false;
    }

    private boolean isWordCharacter(char value) {
        return value == '_' || Character.isLetterOrDigit(value);
    }

    public record Candidate(Long nodeId, double score, List<String> reasons) {
        public Candidate(Long nodeId, double score, String reason) { this(nodeId, score, List.of(reason)); }
    }
    public record Retrieval(StoryBible storyBible, List<Candidate> candidates, StoryBibleRetrievalTrace trace) {
        public Retrieval {
            candidates = List.copyOf(candidates == null ? List.of() : candidates);
            trace = trace == null ? StoryBibleRetrievalTrace.EMPTY : trace;
        }

        public Retrieval(StoryBible storyBible, List<Candidate> candidates, boolean semanticUnavailable) {
            this(storyBible, candidates, new StoryBibleRetrievalTrace(
                    !semanticUnavailable, 0, 0, 0, 0,
                    candidates == null ? 0 : candidates.size(),
                    candidates == null ? List.of() : candidates.stream()
                            .map(candidate -> new StoryBibleRetrievalTrace.Candidate(
                                    candidate.nodeId(), candidate.score(), candidate.reasons()))
                            .toList()));
        }

        public boolean semanticUnavailable() {
            return !trace.semanticRetrieverAvailable();
        }
    }
}
