package com.penmate.backend.application.agent.context;

public interface ContextEpochSnapshotCache {

    String get(Long epochId);

    void put(Long epochId, String snapshot);
}
