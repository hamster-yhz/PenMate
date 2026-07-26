package com.penmate.backend.application.iam;

import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.repository.IamGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityAuthorizationServiceTest {

    private final IamGateway iam = mock(IamGateway.class);
    private final AuthorizationSnapshotCache snapshots = mock(AuthorizationSnapshotCache.class);
    private final CapabilityAuthorizationService service = new CapabilityAuthorizationService(iam, snapshots);

    @Test
    void reuses_the_cached_permissions_when_the_authorization_version_matches() {
        when(iam.findAuthorizationVersion(7L)).thenReturn(4L);
        when(snapshots.get(7L)).thenReturn(new AuthorizationSnapshotCache.CachedAuthorizationSnapshot(
                7L, 4L, Set.of(IamPermissionCodes.APP_ACCESS)));

        CapabilityAuthorizationService.AuthorizationSnapshot result = service.currentSnapshot(7L);

        assertThat(result.version()).isEqualTo(4L);
        assertThat(result.permissions()).containsExactly(IamPermissionCodes.APP_ACCESS);
        verify(iam, never()).findPermissionsByUserId(7L);
        verify(snapshots, never()).put(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reloads_permissions_and_refreshes_redis_when_the_version_changes() {
        when(iam.findAuthorizationVersion(7L)).thenReturn(5L);
        when(snapshots.get(7L)).thenReturn(new AuthorizationSnapshotCache.CachedAuthorizationSnapshot(
                7L, 4L, Set.of(IamPermissionCodes.APP_ACCESS)));
        when(iam.findPermissionsByUserId(7L)).thenReturn(List.of(
                permission(IamPermissionCodes.APP_ACCESS),
                permission(IamPermissionCodes.MODEL_OFFICIAL_USE)));

        CapabilityAuthorizationService.AuthorizationSnapshot result = service.currentSnapshot(7L);

        assertThat(result.version()).isEqualTo(5L);
        assertThat(result.permissions()).containsExactlyInAnyOrder(
                IamPermissionCodes.APP_ACCESS, IamPermissionCodes.MODEL_OFFICIAL_USE);
        verify(snapshots).put(argThat(snapshot -> snapshot.userId().equals(7L)
                && snapshot.version() == 5L
                && snapshot.permissions().containsAll(result.permissions())));
    }

    private static IamPermission permission(String code) {
        IamPermission permission = new IamPermission();
        permission.setCode(code);
        return permission;
    }
}
