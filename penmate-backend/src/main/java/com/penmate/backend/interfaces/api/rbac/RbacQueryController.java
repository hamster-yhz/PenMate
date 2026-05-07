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

/**
 * RBAC 与 IAM 管理控制器。
 * <p>提供用户、角色、权限、菜单及用户-角色/角色-权限绑定关系的查询与维护接口。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class RbacQueryController {

    private final IamQueryApplicationService iamQueryApplicationService;

    public RbacQueryController(IamQueryApplicationService iamQueryApplicationService) {
        this.iamQueryApplicationService = iamQueryApplicationService;
    }

    /**
     * 查询用户列表（脱敏视图）。
     * <p><b>业务目的：</b>返回用户基础信息并屏蔽敏感字段，供后台用户管理页展示。</p>
     * <p><b>流程主线：</b>查询用户集合 -> 转换为安全视图 -> 返回统一响应。</p>
     * <p><b>关键调用：</b>{@code iamQueryApplicationService.listUsers()} 与 {@link #toSafeUser(IamUser)}。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     */
    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<Map<String, Object>> items = iamQueryApplicationService.listUsers().stream().map(this::toSafeUser).toList();
        return ApiResponse.success(items, traceId);
    }

    /**
     * 查询单个用户详情（脱敏视图）。
     * <p><b>流程主线：</b>按用户业务ID查询用户 -> 转换安全视图 -> 返回。</p>
     */
    @GetMapping("/users/{userId}")
    public ApiResponse<Map<String, Object>> user(@PathVariable Long userId,
                                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(toSafeUser(iamQueryApplicationService.getUser(userId)), traceId);
    }

    /**
     * 查询角色列表。
     * <p><b>业务目的：</b>返回系统角色用于用户授权与角色管理。</p>
     */
    @GetMapping("/roles")
    public ApiResponse<List<Map<String, Object>>> roles(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<Map<String, Object>> items = iamQueryApplicationService.listRoles().stream().map(this::toRoleView).toList();
        return ApiResponse.success(items, traceId);
    }

    /**
     * 查询指定用户已绑定角色。
     * <p><b>业务目的：</b>返回用户当前拥有的角色集合，供管理端授权视图展示。</p>
     */
    @GetMapping("/users/{userId}/roles")
    public ApiResponse<List<Map<String, Object>>> userRoles(@PathVariable Long userId,
                                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<Map<String, Object>> items = iamQueryApplicationService.listUserRoles(userId).stream().map(this::toRoleView).toList();
        return ApiResponse.success(items, traceId);
    }

    /**
     * 查询权限点列表。
     * <p><b>业务目的：</b>返回系统权限点用于角色授权配置。</p>
     */
    @GetMapping("/permissions")
    public ApiResponse<List<Map<String, Object>>> permissions(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<Map<String, Object>> items = iamQueryApplicationService.listPermissions().stream().map(this::toPermissionView).toList();
        return ApiResponse.success(items, traceId);
    }

    /**
     * 查询指定角色已绑定权限。
     * <p><b>业务目的：</b>返回角色当前拥有的权限集合，供管理端授权视图展示。</p>
     */
    @GetMapping("/roles/{roleId}/permissions")
    public ApiResponse<List<Map<String, Object>>> rolePermissions(@PathVariable Long roleId,
                                                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<Map<String, Object>> items = iamQueryApplicationService.listRolePermissions(roleId).stream().map(this::toPermissionView).toList();
        return ApiResponse.success(items, traceId);
    }

    /**
     * 创建用户。
     * <p><b>流程主线：</b>校验请求体 -> 调用应用服务创建用户 -> 转换安全视图返回。</p>
     * <p><b>副作用：</b>新增用户记录。</p>
     */
    @PostMapping("/users")
    public ApiResponse<Map<String, Object>> createUser(@Valid @RequestBody CreateUserDto dto,
                                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        IamUser user = iamQueryApplicationService.createUser(dto.getEmail(), dto.getDisplayName(), dto.getStatus(), dto.getAuthMethod());
        return ApiResponse.success(toSafeUser(user), traceId);
    }

    /**
     * 更新用户基础信息。
     * <p><b>业务目的：</b>维护用户显示名与状态。</p>
     * <p><b>ID 语义：</b>路径中的 userId 为用户业务 ID。</p>
     * <p><b>副作用：</b>更新用户记录。</p>
     */
    @PutMapping("/users/{userId}")
    public ApiResponse<Map<String, Object>> updateUser(@PathVariable Long userId,
                                                        @Valid @RequestBody UpdateUserDto dto,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        IamUser user = iamQueryApplicationService.updateUser(userId, dto.getDisplayName(), dto.getStatus());
        return ApiResponse.success(toSafeUser(user), traceId);
    }

    /**
     * 删除用户。
     * <p><b>流程主线：</b>按用户业务ID删除 -> 返回删除结果标记。</p>
     * <p><b>副作用：</b>删除用户及关联关系（按应用层策略）。</p>
     */
    @DeleteMapping("/users/{userId}")
    public ApiResponse<Map<String, Object>> deleteUser(@PathVariable Long userId,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        iamQueryApplicationService.deleteUser(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", true);
        return ApiResponse.success(data, traceId);
    }

    /**
     * 创建角色。
     * <p><b>业务目的：</b>新增可授权角色。</p>
     * <p><b>副作用：</b>新增角色记录。</p>
     */
    @PostMapping("/roles")
    public ApiResponse<Map<String, Object>> createRole(@Valid @RequestBody CreateRoleDto dto,
                                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        IamRole role = iamQueryApplicationService.createRole(dto.getName(), dto.getCode(), dto.getDescription(), dto.getIsSystem());
        return ApiResponse.success(toRoleView(role), traceId);
    }

    /**
     * 更新角色信息。
     * <p><b>业务目的：</b>调整角色名称与描述，不变更角色编码。</p>
     * <p><b>ID 语义：</b>路径中的 roleId 为角色业务 ID。</p>
     * <p><b>副作用：</b>更新角色记录。</p>
     */
    @PutMapping("/roles/{roleId}")
    public ApiResponse<Map<String, Object>> updateRole(@PathVariable Long roleId,
                                                        @Valid @RequestBody UpdateRoleDto dto,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        IamRole role = iamQueryApplicationService.updateRole(roleId, dto.getName(), dto.getDescription());
        return ApiResponse.success(toRoleView(role), traceId);
    }

    /**
     * 删除角色。
     * <p><b>业务目的：</b>移除不再使用的角色。</p>
     * <p><b>ID 语义：</b>路径中的 roleId 为角色业务 ID。</p>
     * <p><b>副作用：</b>删除角色及关联关系（按应用层策略）。</p>
     */
    @DeleteMapping("/roles/{roleId}")
    public ApiResponse<Map<String, Object>> deleteRole(@PathVariable Long roleId,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        iamQueryApplicationService.deleteRole(roleId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", true);
        return ApiResponse.success(data, traceId);
    }

    /**
     * 给用户绑定角色。
     * <p><b>流程主线：</b>读取用户业务ID与角色业务ID -> 调用绑定服务 -> 返回绑定结果。</p>
     * <p><b>副作用：</b>新增用户-角色关系。</p>
     */
    @PostMapping("/users/{userId}/roles")
    public ApiResponse<Map<String, Object>> assignRole(@PathVariable Long userId,
                                                         @RequestParam("roleId") Long roleId,
                                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        iamQueryApplicationService.assignRoleToUser(userId, roleId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bound", true);
        return ApiResponse.success(data, traceId);
    }

    /**
     * 解除用户角色绑定。
     * <p><b>副作用：</b>删除用户-角色关系。</p>
     * <p><b>ID 语义：</b>userId、roleId 均为业务语义 ID。</p>
     */
    @DeleteMapping("/users/{userId}/roles/{roleId}")
    public ApiResponse<Map<String, Object>> removeRole(@PathVariable Long userId,
                                                        @PathVariable Long roleId,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        iamQueryApplicationService.removeRoleFromUser(userId, roleId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("unbound", true);
        return ApiResponse.success(data, traceId);
    }

    /**
     * 给角色绑定权限。
     * <p><b>业务目的：</b>扩展角色可执行能力。</p>
     * <p><b>ID 语义：</b>路径中的 roleId 为角色业务 ID，permissionId 为权限业务 ID。</p>
     * <p><b>副作用：</b>新增角色-权限关系。</p>
     */
    @PostMapping("/roles/{roleId}/permissions")
    public ApiResponse<Map<String, Object>> assignPermission(@PathVariable Long roleId,
                                                               @RequestParam("permissionId") Long permissionId,
                                                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        iamQueryApplicationService.assignPermissionToRole(roleId, permissionId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bound", true);
        return ApiResponse.success(data, traceId);
    }

    /**
     * 解除角色权限绑定。
     * <p><b>副作用：</b>删除角色-权限关系。</p>
     * <p><b>ID 语义：</b>roleId、permissionId 均为业务语义 ID。</p>
     */
    @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
    public ApiResponse<Map<String, Object>> removePermission(@PathVariable Long roleId,
                                                              @PathVariable Long permissionId,
                                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        iamQueryApplicationService.removePermissionFromRole(roleId, permissionId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("unbound", true);
        return ApiResponse.success(data, traceId);
    }

    /**
     * 查询系统菜单树。
     * <p><b>业务目的：</b>返回全量菜单定义用于管理端菜单配置展示。</p>
     */
    @GetMapping("/menus")
    public ApiResponse<List<Map<String, Object>>> menus(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<Map<String, Object>> items = iamQueryApplicationService.listMenus().stream().map(this::toMenuView).toList();
        return ApiResponse.success(items, traceId);
    }

    /**
     * 查询当前用户可见菜单。
     * <p><b>业务目的：</b>按用户角色权限过滤菜单，用于前端动态路由渲染。</p>
     * <p><b>ID 语义：</b>userId 为用户业务ID。</p>
     */
    @GetMapping("/profile/menus")
    public ApiResponse<List<Map<String, Object>>> profileMenus(@RequestParam("userId") Long userId,
                                                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<Map<String, Object>> items = iamQueryApplicationService.listProfileMenus(userId).stream().map(this::toMenuView).toList();
        return ApiResponse.success(items, traceId);
    }

    /**
     * 将领域用户对象转换为脱敏输出结构。
     * <p>仅保留前端展示所需字段，避免泄露敏感信息。</p>
     */
    private Map<String, Object> toSafeUser(IamUser user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getUserId());
        data.put("email", user.getEmail());
        data.put("displayName", user.getDisplayName());
        data.put("status", user.getStatus());
        data.put("authMethod", user.getAuthMethod());
        data.put("lastLoginAt", user.getLastLoginAt());
        return data;
    }

    private Map<String, Object> toRoleView(IamRole role) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", role.getRoleId());
        data.put("name", role.getName());
        data.put("code", role.getCode());
        data.put("description", role.getDescription());
        data.put("isSystem", role.getIsSystem());
        return data;
    }

    private Map<String, Object> toPermissionView(IamPermission permission) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", permission.getPermissionId());
        data.put("name", permission.getName());
        data.put("code", permission.getCode());
        data.put("module", permission.getModule());
        data.put("description", permission.getDescription());
        return data;
    }

    private Map<String, Object> toMenuView(IamMenu menu) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", menu.getMenuId() != null ? menu.getMenuId() : menu.getId());
        if (menu.getParentId() != null) {
            data.put("parentId", menu.getParentId());
        }
        data.put("title", menu.getTitle());
        data.put("path", menu.getPath());
        data.put("sortOrder", menu.getSortOrder());
        data.put("permissionCode", menu.getPermissionCode());
        data.put("visible", menu.getVisible());
        return data;
    }
}

