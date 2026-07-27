package com.penmate.backend.domain.agent.repository;

import com.penmate.backend.domain.agent.model.AgentSessionContextSummary;

public interface AgentSessionContextSummaryRepository {
    AgentSessionContextSummary find(Long sessionId);

    int upsert(AgentSessionContextSummary summary);
}
