package com.penmate.backend.application.iam;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.repository.IamGateway;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/** Versioned permission snapshots for HTTP-independent authorization checks. */
@Service("capabilityAuthorization")
public class CapabilityAuthorizationService {
    private final IamGateway iam;
    private final AuthorizationSnapshotCache snapshots;

    public CapabilityAuthorizationService(IamGateway iam, AuthorizationSnapshotCache snapshots) {
        this.iam = iam;
        this.snapshots = snapshots;
    }

    public boolean has(Long userId, String permissionCode) {
        if (userId == null || permissionCode == null || permissionCode.isBlank()) return false;
        return currentSnapshot(userId).permissions().contains(permissionCode);
    }

    public void require(Long userId, String permissionCode) {
        if (!has(userId, permissionCode)) {
            throw BusinessException.forbidden("Missing permission: " + permissionCode);
        }
    }

    public AuthorizationSnapshot currentSnapshot(Long userId) {
        Long version = iam.findAuthorizationVersion(userId);
        if (version == null) throw BusinessException.unauthorized("User authorization is unavailable");
        AuthorizationSnapshotCache.CachedAuthorizationSnapshot cached = snapshots.get(userId);
        if (cached != null && cached.version() == version) {
            return new AuthorizationSnapshot(cached.userId(), cached.version(), cached.permissions());
        }
        Set<String> permissions = iam.findPermissionsByUserId(userId).stream()
                .map(IamPermission::getCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        AuthorizationSnapshot refreshed = new AuthorizationSnapshot(userId, version, permissions);
        snapshots.put(new AuthorizationSnapshotCache.CachedAuthorizationSnapshot(
                refreshed.userId(), refreshed.version(), refreshed.permissions()));
        return refreshed;
    }

    public void evict(Long userId) {
        if (userId != null) snapshots.evict(userId);
    }

    public record AuthorizationSnapshot(Long userId, long version, Set<String> permissions) { }
}
