package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.context.model.AgentWorkingSetEntry;
import com.penmate.backend.domain.agent.context.repository.AgentWorkingSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
public class AgentWorkingSetService {
    static final int RETENTION_TURNS = 8;
    static final int AUTOMATIC_CAP = 30;

    private final AgentWorkingSetRepository repository;

    public AgentWorkingSetService(AgentWorkingSetRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<AgentWorkingSetEntry> list(Long sessionId) {
        return repository.findBySessionId(sessionId);
    }

    @Transactional
    public List<AgentWorkingSetEntry> promote(Long sessionId, Long turnId, List<Long> nodeIds, BigDecimal score) {
        BigDecimal resolvedScore = score == null || score.signum() <= 0 ? BigDecimal.ONE : score;
        for (Long nodeId : new LinkedHashSet<>(nodeIds == null ? List.<Long>of() : nodeIds)) {
            if (nodeId == null) continue;
            if (repository.promote(sessionId, nodeId, resolvedScore, turnId) != 1) {
                throw BusinessException.of("Failed to promote Story Bible Working Set node");
            }
        }
        repository.evictExpired(sessionId, turnId, RETENTION_TURNS);
        repository.evictOverflow(sessionId, AUTOMATIC_CAP);
        return repository.findBySessionId(sessionId);
    }

    @Transactional
    public void setPinned(Long sessionId, Long nodeId, boolean pinned) {
        if (repository.setPinned(sessionId, nodeId, pinned) != 1) {
            throw BusinessException.notFound("Working Set node not found");
        }
    }
}
