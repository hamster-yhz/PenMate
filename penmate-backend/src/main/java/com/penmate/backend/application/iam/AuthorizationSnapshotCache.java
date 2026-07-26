package com.penmate.backend.application.iam;

import java.util.Set;

/** Redis-facing cache contract for versioned authorization snapshots. */
public interface AuthorizationSnapshotCache {
    CachedAuthorizationSnapshot get(Long userId);

    void put(CachedAuthorizationSnapshot snapshot);

    void evict(Long userId);

    record CachedAuthorizationSnapshot(Long userId, long version, Set<String> permissions) { }
}
