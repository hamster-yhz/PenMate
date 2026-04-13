package com.penmate.backend.interfaces.api.rbac;

import com.penmate.backend.application.iam.IamQueryApplicationService;
import com.penmate.backend.domain.iam.model.IamMenu;
import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.rbac.dto.CreateRoleDto;
import com.penmate.backend.interfaces.api.rbac.dto.CreateUserDto;
import com.penmate.backend.interfaces.api.rbac.dto.UpdateRoleDto;
import com.penmate.backend.interfaces.api.rbac.dto.UpdateUserDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class RbacQueryController {

    private final IamQueryApplicationService iamQueryApplicationService;

    public RbacQueryController(IamQueryApplicationService iamQueryApplicationService) {
        this.iamQueryApplicationService = iamQueryApplicationService;
    }

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<Map<String, Object>> items = iamQueryApplicationService.listUsers().stream().map(this::toSafeUser).toList();
        return ApiResponse.success(items, traceId);
    }

    @GetMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> user(@PathVariable Long id,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(toSafeUser(iamQueryApplicationService.getUser(id)), traceId);
    }

    @GetMapping("/roles")
    public ApiResponse<List<IamRole>> roles(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(iamQueryApplicationService.listRoles(), traceId);
    }

    @GetMapping("/permissions")
    public ApiResponse<List<IamPermission>> permissions(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(iamQueryApplicationService.listPermissions(), traceId);
    }

    @PostMapping("/users")
    public ApiResponse<Map<String, Object>> createUser(@Valid @RequestBody CreateUserDto dto,
                                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        IamUser user = iamQueryApplicationService.createUser(dto.getEmail(), dto.getDisplayName(), dto.getStatus(), dto.getAuthMethod());
        return ApiResponse.success(toSafeUser(user), traceId);
    }

    @PutMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> updateUser(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateUserDto dto,
                                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        IamUser user = iamQueryApplicationService.updateUser(id, dto.getDisplayName(), dto.getStatus());
        return ApiResponse.success(toSafeUser(user), traceId);
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> deleteUser(@PathVariable Long id,
                                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        iamQueryApplicationService.deleteUser(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", true);
        return ApiResponse.success(data, traceId);
    }

    @PostMapping("/roles")
    public ApiResponse<IamRole> createRole(@Valid @RequestBody CreateRoleDto dto,
                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        IamRole role = iamQueryApplicationService.createRole(dto.getName(), dto.getCode(), dto.getDescription(), dto.getIsSystem());
        return ApiResponse.success(role, traceId);
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<IamRole> updateRole(@PathVariable Long id,
                                           @Valid @RequestBody UpdateRoleDto dto,
                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        IamRole role = iamQueryApplicationService.updateRole(id, dto.getName(), dto.getDescription());
        return ApiResponse.success(role, traceId);
    }

    @DeleteMapping("/roles/{id}")
    public ApiResponse<Map<String, Object>> deleteRole(@PathVariable Long id,
                                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        iamQueryApplicationService.deleteRole(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", true);
        return ApiResponse.success(data, traceId);
    }

    @PostMapping("/users/{id}/roles")
    public ApiResponse<Map<String, Object>> assignRole(@PathVariable Long id,
                                                        @RequestParam("roleId") Long roleId,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        iamQueryApplicationService.assignRoleToUser(id, roleId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bound", true);
        return ApiResponse.success(data, traceId);
    }

    @DeleteMapping("/users/{userId}/roles/{roleId}")
    public ApiResponse<Map<String, Object>> removeRole(@PathVariable Long userId,
                                                        @PathVariable Long roleId,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        iamQueryApplicationService.removeRoleFromUser(userId, roleId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("unbound", true);
        return ApiResponse.success(data, traceId);
    }

    @PostMapping("/roles/{id}/permissions")
    public ApiResponse<Map<String, Object>> assignPermission(@PathVariable Long id,
                                                              @RequestParam("permissionId") Long permissionId,
                                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        iamQueryApplicationService.assignPermissionToRole(id, permissionId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bound", true);
        return ApiResponse.success(data, traceId);
    }

    @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
    public ApiResponse<Map<String, Object>> removePermission(@PathVariable Long roleId,
                                                              @PathVariable Long permissionId,
                                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        iamQueryApplicationService.removePermissionFromRole(roleId, permissionId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("unbound", true);
        return ApiResponse.success(data, traceId);
    }

    @GetMapping("/menus")
    public ApiResponse<List<IamMenu>> menus(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(iamQueryApplicationService.listMenus(), traceId);
    }

    @GetMapping("/profile/menus")
    public ApiResponse<List<IamMenu>> profileMenus(@RequestParam("userId") Long userId,
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(iamQueryApplicationService.listProfileMenus(userId), traceId);
    }

    private Map<String, Object> toSafeUser(IamUser user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("email", user.getEmail());
        data.put("displayName", user.getDisplayName());
        data.put("status", user.getStatus());
        data.put("authMethod", user.getAuthMethod());
        data.put("lastLoginAt", user.getLastLoginAt());
        return data;
    }
}

