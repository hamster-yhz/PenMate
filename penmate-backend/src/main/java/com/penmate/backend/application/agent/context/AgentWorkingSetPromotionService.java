package com.penmate.backend.application.agent.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AgentWorkingSetPromotionService {
    private static final Logger log = LoggerFactory.getLogger(AgentWorkingSetPromotionService.class);
    private static final int MAX_ATTEMPTS = 2;

    private final AgentWorkingSetService workingSet;

    public AgentWorkingSetPromotionService(AgentWorkingSetService workingSet) {
        this.workingSet = workingSet;
    }

    public void promoteBestEffort(Long sessionId, Long turnId, List<Long> nodeIds, BigDecimal score) {
        if (nodeIds == null || nodeIds.isEmpty()) return;
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                workingSet.promote(sessionId, turnId, nodeIds, score);
                return;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                log.warn("Working Set promotion failed: sessionId={}, turnId={}, attempt={}, nodeCount={}",
                        sessionId, turnId, attempt, nodeIds.size(), ex);
            }
        }
        log.error("Working Set promotion abandoned without changing resolved Run context: sessionId={}, turnId={}, nodeCount={}",
                sessionId, turnId, nodeIds.size(), lastFailure);
    }
}
