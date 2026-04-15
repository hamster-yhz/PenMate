package com.penmate.backend.application.iam;

import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IamQueryApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private IamGateway iamGateway;

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
        when(iamGateway.findUserById(1L)).thenReturn(null);
        assertThatThrownBy(() -> iamQueryApplicationService.getUser(1L))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found");
    }

    @Test
    void UT_APP_IAM_DELETE_SYSTEM_ROLE_FORBIDDEN() {
        IamRole role = new IamRole();
        role.setId(1L);
        role.setIsSystem(true);
        when(iamGateway.findRoleById(1L)).thenReturn(role);

        assertThatThrownBy(() -> iamQueryApplicationService.deleteRole(1L))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("System role cannot be deleted");
    }
}

