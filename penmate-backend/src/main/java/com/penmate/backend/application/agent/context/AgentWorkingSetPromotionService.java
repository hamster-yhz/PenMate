package com.penmate.backend.application.agent.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AgentWorkingSetPromotionService {
    private static final Logger log = LoggerFactory.getLogger(AgentWorkingSetPromotionService.class);
    private static final int MAX_ATTEMPTS = 2;

    private final AgentWorkingSetService workingSet;

    public AgentWorkingSetPromotionService(AgentWorkingSetService workingSet) {
        this.workingSet = workingSet;
    }

    public PromotionSummary promoteBestEffort(Long sessionId, Long turnId, List<Long> nodeIds, BigDecimal score) {
        Set<Long> candidates = new LinkedHashSet<>(nodeIds == null ? List.of() : nodeIds);
        candidates.remove(null);
        if (candidates.isEmpty()) return new PromotionSummary(0, 0, 0, true);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Set<Long> before = ids(workingSet.list(sessionId));
                Set<Long> after = ids(workingSet.promote(sessionId, turnId, List.copyOf(candidates), score));
                int promoted = (int) candidates.stream().filter(after::contains).count();
                int evicted = (int) before.stream().filter(nodeId -> !after.contains(nodeId)).count();
                return new PromotionSummary(candidates.size(), promoted, evicted, true);
            } catch (RuntimeException ex) {
                lastFailure = ex;
                log.warn("Working Set promotion failed: sessionId={}, turnId={}, attempt={}, nodeCount={}",
                        sessionId, turnId, attempt, nodeIds.size(), ex);
            }
        }
        log.error("Working Set promotion abandoned without changing resolved Run context: sessionId={}, turnId={}, nodeCount={}",
                sessionId, turnId, nodeIds.size(), lastFailure);
        return new PromotionSummary(candidates.size(), 0, 0, false);
    }

    private Set<Long> ids(List<com.penmate.backend.domain.agent.context.model.AgentWorkingSetEntry> entries) {
        Set<Long> result = new LinkedHashSet<>();
        if (entries != null) entries.forEach(entry -> result.add(entry.nodeId()));
        return result;
    }

    public record PromotionSummary(int candidateCount, int promotedCount, int evictedCount, boolean succeeded) {
    }
}
