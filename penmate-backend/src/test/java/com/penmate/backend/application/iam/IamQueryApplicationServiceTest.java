package com.penmate.backend.application.iam;

import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IamQueryApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private IamGateway iamGateway;

    @Mock private BusinessIdGenerator businessIdGenerator;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AdminSafetyPolicy adminSafetyPolicy;
    @Mock private AuthorizationChangeDispatcher authorizationChanges;

    @InjectMocks
    private IamQueryApplicationService iamQueryApplicationService;

    @Test
    void UT_APP_IAM_LIST_USERS_SUCCESS() {
        when(iamGateway.findAllUsers()).thenReturn(List.of(new IamUser(), new IamUser()));
        assertThat(iamQueryApplicationService.listUsers()).hasSize(2);
        verify(iamGateway).findAllUsers();
    }

    @Test
    void UT_APP_IAM_GET_USER_NOT_FOUND() {
        when(iamGateway.findUserByUserId(1L)).thenReturn(null);
        assertThatThrownBy(() -> iamQueryApplicationService.getUser(1L))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("User not found");
    }

    @Test
    void UT_APP_IAM_LIST_USER_ROLES_SUCCESS() {
        IamRole role = new IamRole();
        role.setRoleId(2001L);
        role.setCode("ADMIN");
        when(iamGateway.findRolesByUserId(1001L)).thenReturn(List.of(role));

        assertThat(iamQueryApplicationService.listUserRoles(1001L)).extracting(IamRole::getCode).containsExactly("ADMIN");
        verify(iamGateway).findRolesByUserId(1001L);
    }

    @Test
    void UT_APP_IAM_LIST_ROLE_PERMISSIONS_SUCCESS() {
        IamPermission permission = new IamPermission();
        permission.setPermissionId(3001L);
        permission.setCode("rbac.manage");
        when(iamGateway.findPermissionsByRoleId(2001L)).thenReturn(List.of(permission));

        assertThat(iamQueryApplicationService.listRolePermissions(2001L))
                .extracting(IamPermission::getCode)
                .containsExactly("rbac.manage");
        verify(iamGateway).findPermissionsByRoleId(2001L);
    }

    @Test
    void UT_APP_IAM_DELETE_SYSTEM_ROLE_FORBIDDEN() {
        IamRole role = new IamRole();
        role.setId(1L);
        role.setIsSystem(true);
        when(iamGateway.findRoleByRoleId(1L)).thenReturn(role);

        assertThatThrownBy(() -> iamQueryApplicationService.deleteRole(1L, 1001L))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("System role cannot be deleted");
    }

    @Test
    void creates_a_local_user_with_encoded_password_and_default_user_role() {
        IamRole defaultRole = new IamRole();
        defaultRole.setRoleId(2L);
        defaultRole.setCode(SystemRoleCodes.USER);
        when(iamGateway.findRoleByCode(SystemRoleCodes.USER)).thenReturn(defaultRole);
        when(businessIdGenerator.nextId()).thenReturn(1001L);
        when(passwordEncoder.encode("initial-pass")).thenReturn("$2a$encoded");

        IamUser created = iamQueryApplicationService.createUser(
                "Writer@Example.com", "Writer", 1, "initial-pass");

        assertThat(created.getUserId()).isEqualTo(1001L);
        assertThat(created.getEmail()).isEqualTo("writer@example.com");
        assertThat(created.getAuthMethod()).isEqualTo("local");
        assertThat(created.getPasswordHash()).isEqualTo("$2a$encoded");
        verify(iamGateway).addUserRoles(1001L, List.of(2L));
    }

}


