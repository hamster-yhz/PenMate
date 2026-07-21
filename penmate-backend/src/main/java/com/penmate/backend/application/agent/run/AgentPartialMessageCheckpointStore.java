package com.penmate.backend.application.agent.run;

import java.time.Instant;
import java.util.Optional;

public interface AgentPartialMessageCheckpointStore {

    void save(Snapshot snapshot);

    Optional<Snapshot> find(Long runId);

    void delete(Long runId);

    record Snapshot(Long runId, Long turnId, String text, long offset, Instant updatedAt) {
        public Snapshot {
            text = text == null ? "" : text;
            updatedAt = updatedAt == null ? Instant.now() : updatedAt;
        }
    }
}
