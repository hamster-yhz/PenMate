package com.penmate.backend.application.iam;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.repository.IamGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminSafetyPolicyTest {
    private final IamGateway iam = mock(IamGateway.class);
    private final AdminSafetyPolicy policy = new AdminSafetyPolicy(iam);
    private final IamRole admin = role(1L, SystemRoleCodes.ADMIN);

    @BeforeEach
    void lockableAdminRole() {
        when(iam.findRoleByCode(SystemRoleCodes.ADMIN)).thenReturn(admin);
        when(iam.lockRoleRbacRevision(1L)).thenReturn(0L);
    }

    @Test
    void protects_the_emergency_administrator_from_demotion() {
        assertThatThrownBy(() -> policy.requireRoleReplacementAllowed(
                2L, SystemRoleCodes.BOOTSTRAP_ADMIN_USER_ID, List.of(admin), List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("The emergency administrator cannot be demoted");
    }

    @Test
    void prevents_an_administrator_from_removing_their_own_admin_role() {
        assertThatThrownBy(() -> policy.requireRoleReplacementAllowed(
                7L, 7L, List.of(admin), List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Administrators cannot remove their own administrator role");
    }

    @Test
    void prevents_removing_the_last_active_administrator() {
        when(iam.countActiveUsersByRoleCode(SystemRoleCodes.ADMIN)).thenReturn(1);

        assertThatThrownBy(() -> policy.requireRoleReplacementAllowed(
                7L, 8L, List.of(admin), List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("At least one active administrator is required");
    }

    private static IamRole role(Long roleId, String code) {
        IamRole role = new IamRole();
        role.setRoleId(roleId);
        role.setCode(code);
        return role;
    }
}
