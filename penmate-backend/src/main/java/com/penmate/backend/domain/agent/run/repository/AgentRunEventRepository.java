package com.penmate.backend.domain.agent.run.repository;

import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentEventWindow;

import java.time.LocalDateTime;
import java.util.List;

public interface AgentRunEventRepository {

    AgentEvent append(Long runId, String eventType, String payloadJson);

    List<AgentEvent> listAfter(Long runId, Long after);

    List<Long> findTerminalRunIdsWithEventsBefore(LocalDateTime cutoff, int limit);

    int deleteThrough(Long runId, Long maxSequence);

    AgentEventWindow findWindow(Long runId);
}
