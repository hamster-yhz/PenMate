package com.penmate.backend.application.agent.run;

import java.time.Duration;

public interface AgentCheckpointCache {

    void put(Long runId, String serializedState, Duration ttl);

    String get(Long runId);

    void delete(Long runId);
}
