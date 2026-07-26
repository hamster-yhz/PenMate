package com.penmate.backend.application.iam;

import com.penmate.backend.domain.iam.model.IamRbacAssignmentAudit;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.repository.IamGateway;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IamRbacAssignmentApplicationServiceTest {
    @Mock private IamGateway iamGateway;
    @Mock private BusinessIdGenerator businessIdGenerator;
    @Mock private AdminSafetyPolicy adminSafetyPolicy;
    @Mock private AuthorizationChangeDispatcher authorizationChanges;
    @InjectMocks private IamRbacAssignmentApplicationService service;

    @Test
    void replaces_user_roles_atomically_and_records_one_complete_audit() {
        IamRole previousRole = role(2001L, "ADMIN");
        IamRole nextRole = role(2002L, "EDITOR");
        when(iamGateway.lockUserRbacRevision(1001L)).thenReturn(3L);
        when(iamGateway.findRoleByRoleId(2002L)).thenReturn(nextRole);
        when(iamGateway.findRolesByUserId(1001L)).thenReturn(List.of(previousRole));
        when(iamGateway.replaceUserRoles(1001L, List.of(2002L), 3L)).thenReturn(1);
        when(businessIdGenerator.nextId()).thenReturn(9001L);

        RbacAssignmentSnapshot<IamRole> result = service.replaceUserRoles(
                1001L, List.of(2002L), 3L, 7001L, "trace-rbac");

        assertThat(result.revision()).isEqualTo(4L);
        assertThat(result.items()).extracting(IamRole::getRoleId).containsExactly(2002L);
        ArgumentCaptor<IamRbacAssignmentAudit> audit = ArgumentCaptor.forClass(IamRbacAssignmentAudit.class);
        verify(iamGateway).insertRbacAssignmentAudit(audit.capture());
        assertThat(audit.getValue().beforeIds()).containsExactly(2001L);
        assertThat(audit.getValue().afterIds()).containsExactly(2002L);
        assertThat(audit.getValue().actorUserId()).isEqualTo(7001L);
        assertThat(audit.getValue().previousRevision()).isEqualTo(3L);
        assertThat(audit.getValue().newRevision()).isEqualTo(4L);
    }

    @Test
    void rejects_stale_role_permission_revision_without_writing_assignments() {
        when(iamGateway.findRoleByRoleId(2001L)).thenReturn(role(2001L, "CUSTOM"));
        when(iamGateway.lockRoleRbacRevision(2001L)).thenReturn(8L);

        assertThatThrownBy(() -> service.replaceRolePermissions(
                2001L, List.of(3001L), 7L, 7001L, "trace-stale"))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("RBAC assignments changed; refresh and try again");

        verify(iamGateway, never()).replaceRolePermissions(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyLong());
        verify(iamGateway, never()).insertRbacAssignmentAudit(org.mockito.ArgumentMatchers.any());
    }

    private IamRole role(Long id, String code) {
        IamRole role = new IamRole();
        role.setRoleId(id);
        role.setCode(code);
        return role;
    }
}
