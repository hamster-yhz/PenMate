package com.penmate.backend.interfaces.api.rbac;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.iam.IamQueryApplicationService;
import com.penmate.backend.domain.iam.model.IamMenu;
import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.interfaces.api.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RbacQueryControllerTest {

    @Mock
    private IamQueryApplicationService iamQueryApplicationService;

    @InjectMocks
    private RbacQueryController rbacQueryController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(rbacQueryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void UT_RBAC_USERS_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RBAC-USERS-LIST";
        IamUser user = new IamUser();
        user.setId(900001L);
        user.setUserId(1001L);
        user.setEmail("author@penmate.ai");
        user.setDisplayName("作者A");
        user.setStatus(1);
        user.setAuthMethod("local");
        when(iamQueryApplicationService.listUsers()).thenReturn(List.of(user));

        mockMvc().perform(get("/api/v1/users").header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].id").value("1001"))
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data[0].email").value("author@penmate.ai"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_RBAC_USERS_CREATE_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-RBAC-USERS-CREATE-INVALID";

        mockMvc().perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", "作者A",
                                "status", 1
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_RBAC_USERS_CREATE_CONFLICT_EMAIL() throws Exception {
        String traceId = "UT-TRACE-RBAC-USERS-CREATE-CONFLICT";
        doThrow(new IllegalArgumentException("Email already exists"))
                .when(iamQueryApplicationService)
                .createUser(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(), anyString());

        mockMvc().perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "author@penmate.ai",
                                "displayName", "作者A",
                                "status", 1,
                                "authMethod", "local"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.data.message").value("Email already exists"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_RBAC_ASSIGN_ROLE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RBAC-ASSIGN-ROLE-SUCCESS";
        doNothing().when(iamQueryApplicationService).assignRoleToUser(1001L, 2001L);

        mockMvc().perform(post("/api/v1/users/1001/roles")
                        .param("roleId", "2001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bound").value(true))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_RBAC_ASSIGN_ROLE_MISSING_ROLE_ID_BAD_REQUEST() throws Exception {
        String traceId = "UT-TRACE-RBAC-ASSIGN-ROLE-MISSING-ROLE-ID";

        mockMvc().perform(post("/api/v1/users/1001/roles")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.details[0].field").value("roleId"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_RBAC_ASSIGN_ROLE_NOT_FOUND() throws Exception {
        String traceId = "UT-TRACE-RBAC-ASSIGN-ROLE-NOT-FOUND";
        doThrow(new IllegalArgumentException("Role not found"))
                .when(iamQueryApplicationService).assignRoleToUser(1001L, 9999L);

        mockMvc().perform(post("/api/v1/users/1001/roles")
                        .param("roleId", "9999")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_RBAC_USER_ROLES_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RBAC-USER-ROLES-LIST";
        IamRole role = new IamRole();
        role.setId(920001L);
        role.setRoleId(2001L);
        role.setName("管理员");
        role.setCode("ADMIN");
        when(iamQueryApplicationService.listUserRoles(1001L)).thenReturn(List.of(role));

        mockMvc().perform(get("/api/v1/users/1001/roles")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].id").value("2001"))
                .andExpect(jsonPath("$.data[0].roleId").doesNotExist())
                .andExpect(jsonPath("$.data[0].code").value("ADMIN"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));

        verify(iamQueryApplicationService).listUserRoles(1001L);
    }

    @Test
    void UT_RBAC_ROLE_PERMISSIONS_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RBAC-ROLE-PERMISSIONS-LIST";
        IamPermission permission = new IamPermission();
        permission.setId(930001L);
        permission.setPermissionId(3001L);
        permission.setName("RBAC 管理");
        permission.setCode("rbac.manage");
        when(iamQueryApplicationService.listRolePermissions(3001L)).thenReturn(List.of(permission));

        mockMvc().perform(get("/api/v1/roles/3001/permissions")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].id").value("3001"))
                .andExpect(jsonPath("$.data[0].permissionId").doesNotExist())
                .andExpect(jsonPath("$.data[0].code").value("rbac.manage"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));

        verify(iamQueryApplicationService).listRolePermissions(3001L);
    }

    @Test
    void UT_RBAC_PROFILE_MENUS_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RBAC-PROFILE-MENUS";
        IamMenu menu = new IamMenu();
        menu.setId(11L);
        menu.setTitle("工作台");
        menu.setPath("/workbench");
        when(iamQueryApplicationService.listProfileMenus(anyLong())).thenReturn(List.of(menu));

        mockMvc().perform(get("/api/v1/profile/menus")
                        .param("userId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].id").value("11"))
                .andExpect(jsonPath("$.data[0].path").value("/workbench"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_RBAC_ROLES_DELETE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RBAC-ROLES-DELETE";
        doNothing().when(iamQueryApplicationService).deleteRole(3001L);

        mockMvc().perform(delete("/api/v1/roles/3001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_RBAC_ROLES_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RBAC-ROLES-LIST";
        IamRole role = new IamRole();
        role.setId(940001L);
        role.setRoleId(3001L);
        role.setCode("editor");
        role.setName("编辑");
        when(iamQueryApplicationService.listRoles()).thenReturn(List.of(role));

        mockMvc().perform(get("/api/v1/roles").header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].id").value("3001"))
                .andExpect(jsonPath("$.data[0].roleId").doesNotExist())
                .andExpect(jsonPath("$.data[0].code").value("editor"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_RBAC_ROLES_CREATE_CONFLICT_CODE() throws Exception {
        String traceId = "UT-TRACE-RBAC-ROLES-CREATE-CONFLICT";
        doThrow(new IllegalArgumentException("Role code already exists"))
                .when(iamQueryApplicationService).createRole(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());

        mockMvc().perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "编辑",
                                "code", "editor",
                                "description", "desc",
                                "isSystem", false
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void UT_RBAC_ROLES_UPDATE_NOT_FOUND() throws Exception {
        String traceId = "UT-TRACE-RBAC-ROLES-UPDATE-NOT-FOUND";
        doThrow(new IllegalArgumentException("Role not found"))
                .when(iamQueryApplicationService).updateRole(3999L, "新角色", "desc");

        mockMvc().perform(put("/api/v1/roles/3999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "新角色",
                                "description", "desc"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void UT_RBAC_REMOVE_ROLE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RBAC-REMOVE-ROLE";
        doNothing().when(iamQueryApplicationService).removeRoleFromUser(1001L, 2001L);

        mockMvc().perform(delete("/api/v1/users/1001/roles/2001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unbound").value(true))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_RBAC_ASSIGN_ROLE_IDEMPOTENT() throws Exception {
        String traceId = "UT-TRACE-RBAC-ASSIGN-ROLE-IDEMPOTENT";
        doNothing().when(iamQueryApplicationService).assignRoleToUser(1001L, 2001L);

        mockMvc().perform(post("/api/v1/users/1001/roles")
                        .param("roleId", "2001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bound").value(true));
    }

    @Test
    void UT_RBAC_ASSIGN_PERMISSION_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RBAC-ASSIGN-PERMISSION-SUCCESS";
        doNothing().when(iamQueryApplicationService).assignPermissionToRole(3001L, 4001L);

        mockMvc().perform(post("/api/v1/roles/3001/permissions")
                        .param("permissionId", "4001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bound").value(true))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_RBAC_ASSIGN_PERMISSION_IDEMPOTENT() throws Exception {
        String traceId = "UT-TRACE-RBAC-ASSIGN-PERMISSION-IDEMPOTENT";
        doNothing().when(iamQueryApplicationService).assignPermissionToRole(3001L, 4001L);

        mockMvc().perform(post("/api/v1/roles/3001/permissions")
                        .param("permissionId", "4001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bound").value(true));
    }

    @Test
    void UT_RBAC_MENUS_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RBAC-MENUS-LIST";
        IamMenu menu = new IamMenu();
        menu.setId(21L);
        menu.setTitle("系统管理");
        menu.setPath("/system");
        when(iamQueryApplicationService.listMenus()).thenReturn(List.of(menu));

        mockMvc().perform(get("/api/v1/menus").header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(21))
                .andExpect(jsonPath("$.data[0].path").value("/system"));
    }

    @Test
    void UT_RBAC_USERS_DELETE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RBAC-USERS-DELETE";
        doNothing().when(iamQueryApplicationService).deleteUser(1001L);

        mockMvc().perform(delete("/api/v1/users/1001").header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_RBAC_USERS_UPDATE_NOT_FOUND() throws Exception {
        String traceId = "UT-TRACE-RBAC-USERS-UPDATE-NOT-FOUND";
        doThrow(new IllegalArgumentException("User not found"))
                .when(iamQueryApplicationService).updateUser(9999L, "作者B", 1);

        mockMvc().perform(put("/api/v1/users/9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", "作者B",
                                "status", 1
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void UT_RBAC_REJECTS_LEGACY_PREFIX_IDS() throws Exception {
        String traceId = "UT-TRACE-RBAC-LEGACY-ID-REJECT";

        mockMvc().perform(get("/api/v1/users/user-1001/roles")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }
}
