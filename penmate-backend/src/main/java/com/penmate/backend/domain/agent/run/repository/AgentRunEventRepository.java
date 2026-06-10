package com.penmate.backend.domain.agent.run.repository;

import com.penmate.backend.domain.agent.run.model.AgentEvent;

import java.util.List;

public interface AgentRunEventRepository {

    AgentEvent append(Long runId, String eventType, String payloadJson);

    List<AgentEvent> listAfter(Long runId, Long after);
}
