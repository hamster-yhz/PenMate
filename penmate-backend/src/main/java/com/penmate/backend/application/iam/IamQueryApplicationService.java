package com.penmate.backend.application.iam;

import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamMenu;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * IAM 查询与维护应用服务。
 * <p>负责用户、角色、权限、菜单的查询与基础维护，以及用户-角色、角色-权限关系绑定管理。</p>
 */
@Service
@Slf4j
public class IamQueryApplicationService {

    private final IamGateway iamGateway;

    public IamQueryApplicationService(IamGateway iamGateway) {
        this.iamGateway = iamGateway;
    }

    /**
     * 查询用户列表。
     *
     * @return 出参：处理结果
     */
    public List<IamUser> listUsers() {
        List<IamUser> users = iamGateway.findAllUsers();
        log.info("查询用户列表: count={}", users.size());
        return users;
    }

    /**
     * 查询单个用户详情。
     *
     * @param id 入参：id
     * @return 出参：处理结果
     */
    public IamUser getUser(Long id) {
        log.info("查询用户详情: userId={}", id);
        IamUser user = iamGateway.findUserById(id);
        if (user == null) {
            log.warn("查询用户详情失败: userId={}, reason=not_found", id);
            throw com.penmate.backend.application.common.exception.BusinessException.of("User not found");
        }
        log.info("查询用户详情成功: userId={}, email={}", id, user.getEmail());
        return user;
    }

    /**
     * 查询角色列表。
     *
     * @return 出参：处理结果
     */
    public List<IamRole> listRoles() {
        List<IamRole> roles = iamGateway.findAllRoles();
        log.info("查询角色列表: count={}", roles.size());
        return roles;
    }

    /**
     * 查询权限列表。
     *
     * @return 出参：处理结果
     */
    public List<IamPermission> listPermissions() {
        List<IamPermission> permissions = iamGateway.findAllPermissions();
        log.info("查询权限列表: count={}", permissions.size());
        return permissions;
    }

    /**
     * 创建用户。
     *
     * @param email 入参：email
     * @param displayName 入参：displayName
     * @param status 入参：status
     * @param authMethod 入参：authMethod
     * @return 出参：处理结果
     */
    public IamUser createUser(String email, String displayName, Integer status, String authMethod) {
        log.info("创建用户: email={}, displayName={}, status={}, authMethod={}", email, displayName, status, authMethod);
        IamUser user = new IamUser();
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setStatus(status);
        user.setAuthMethod(authMethod == null || authMethod.isBlank() ? "local" : authMethod);
        user.setPasswordHash("{noop}changeme");
        iamGateway.insertUser(user);
        log.info("创建用户成功: userId={}, email={}", user.getId(), user.getEmail());
        return user;
    }

    /**
     * 更新用户基础信息。
     *
     * @param id 入参：id
     * @param displayName 入参：displayName
     * @param status 入参：status
     * @return 出参：处理结果
     */
    public IamUser updateUser(Long id, String displayName, Integer status) {
        log.info("更新用户: userId={}, displayName={}, status={}", id, displayName, status);
        IamUser user = getUser(id);
        user.setDisplayName(displayName);
        user.setStatus(status);
        int affected = iamGateway.updateUserBasic(user);
        if (affected <= 0) {
            log.warn("更新用户失败: userId={}, reason=not_found", id);
            throw com.penmate.backend.application.common.exception.BusinessException.of("User not found");
        }
        log.info("更新用户成功: userId={}", id);
        return getUser(id);
    }

    /**
     * 软删除用户。
     *
     * @param id 入参：id
     */
    public void deleteUser(Long id) {
        log.info("删除用户: userId={}", id);
        int affected = iamGateway.softDeleteUser(id);
        if (affected <= 0) {
            log.warn("删除用户失败: userId={}, reason=not_found", id);
            throw com.penmate.backend.application.common.exception.BusinessException.of("User not found");
        }
        log.info("删除用户成功: userId={}", id);
    }

    /**
     * 创建角色。
     *
     * @param name 入参：name
     * @param code 入参：code
     * @param description 入参：description
     * @param isSystem 入参：isSystem
     * @return 出参：处理结果
     */
    public IamRole createRole(String name, String code, String description, Boolean isSystem) {
        log.info("创建角色: code={}, name={}, isSystem={}", code, name, isSystem);
        IamRole role = new IamRole();
        role.setName(name);
        role.setCode(code);
        role.setDescription(description);
        role.setIsSystem(Boolean.TRUE.equals(isSystem));
        iamGateway.insertRole(role);
        log.info("创建角色成功: roleId={}, code={}", role.getId(), role.getCode());
        return role;
    }

    /**
     * 更新角色基础信息。
     *
     * @param id 入参：id
     * @param name 入参：name
     * @param description 入参：description
     * @return 出参：处理结果
     */
    public IamRole updateRole(Long id, String name, String description) {
        log.info("更新角色: roleId={}, name={}", id, name);
        IamRole role = iamGateway.findRoleById(id);
        if (role == null) {
            log.warn("更新角色失败: roleId={}, reason=not_found", id);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role not found");
        }
        role.setName(name);
        role.setDescription(description);
        int affected = iamGateway.updateRoleBasic(role);
        if (affected <= 0) {
            log.warn("更新角色失败: roleId={}, reason=not_found_after_update", id);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role not found");
        }
        log.info("更新角色成功: roleId={}", id);
        return iamGateway.findRoleById(id);
    }

    /**
     * 删除角色。
     *
     * @param id 入参：id
     */
    public void deleteRole(Long id) {
        log.info("删除角色: roleId={}", id);
        IamRole role = iamGateway.findRoleById(id);
        if (role == null) {
            log.warn("删除角色失败: roleId={}, reason=not_found", id);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role not found");
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            log.warn("删除角色失败: roleId={}, reason=system_role", id);
            throw com.penmate.backend.application.common.exception.BusinessException.of("System role cannot be deleted");
        }
        int affected = iamGateway.softDeleteRole(id);
        if (affected <= 0) {
            log.warn("删除角色失败: roleId={}, reason=not_found_after_delete", id);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role not found");
        }
        log.info("删除角色成功: roleId={}", id);
    }

    /**
     * 绑定用户与角色关系。
     *
     * @param userId 入参：userId
     * @param roleId 入参：roleId
     */
    public void assignRoleToUser(Long userId, Long roleId) {
        log.info("绑定用户角色: userId={}, roleId={}", userId, roleId);
        if (iamGateway.findUserById(userId) == null) {
            log.warn("绑定用户角色失败: userId={}, roleId={}, reason=user_not_found", userId, roleId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("User not found");
        }
        if (iamGateway.findRoleById(roleId) == null) {
            log.warn("绑定用户角色失败: userId={}, roleId={}, reason=role_not_found", userId, roleId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role not found");
        }
        iamGateway.assignRoleToUser(userId, roleId);
        log.info("绑定用户角色成功: userId={}, roleId={}", userId, roleId);
    }

    /**
     * 解绑用户与角色关系。
     *
     * @param userId 入参：userId
     * @param roleId 入参：roleId
     */
    public void removeRoleFromUser(Long userId, Long roleId) {
        log.info("解绑用户角色: userId={}, roleId={}", userId, roleId);
        int affected = iamGateway.removeRoleFromUser(userId, roleId);
        if (affected <= 0) {
            log.warn("解绑用户角色失败: userId={}, roleId={}, reason=relation_not_found", userId, roleId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("User role binding not found");
        }
        log.info("解绑用户角色成功: userId={}, roleId={}", userId, roleId);
    }

    /**
     * 绑定角色与权限关系。
     *
     * @param roleId 入参：roleId
     * @param permissionId 入参：permissionId
     */
    public void assignPermissionToRole(Long roleId, Long permissionId) {
        log.info("绑定角色权限: roleId={}, permissionId={}", roleId, permissionId);
        if (iamGateway.findRoleById(roleId) == null) {
            log.warn("绑定角色权限失败: roleId={}, permissionId={}, reason=role_not_found", roleId, permissionId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role not found");
        }
        if (iamGateway.findPermissionById(permissionId) == null) {
            log.warn("绑定角色权限失败: roleId={}, permissionId={}, reason=permission_not_found", roleId, permissionId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Permission not found");
        }
        iamGateway.assignPermissionToRole(roleId, permissionId);
        log.info("绑定角色权限成功: roleId={}, permissionId={}", roleId, permissionId);
    }

    /**
     * 解绑角色与权限关系。
     *
     * @param roleId 入参：roleId
     * @param permissionId 入参：permissionId
     */
    public void removePermissionFromRole(Long roleId, Long permissionId) {
        log.info("解绑角色权限: roleId={}, permissionId={}", roleId, permissionId);
        int affected = iamGateway.removePermissionFromRole(roleId, permissionId);
        if (affected <= 0) {
            log.warn("解绑角色权限失败: roleId={}, permissionId={}, reason=relation_not_found", roleId, permissionId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role permission binding not found");
        }
        log.info("解绑角色权限成功: roleId={}, permissionId={}", roleId, permissionId);
    }

    /**
     * 查询系统可见菜单。
     *
     * @return 出参：处理结果
     */
    public List<IamMenu> listMenus() {
        List<IamMenu> menus = iamGateway.findVisibleMenus();
        log.info("查询可见菜单: count={}", menus.size());
        return menus;
    }

    /**
     * 查询指定用户可见菜单。
     *
     * @param userId 入参：userId
     * @return 出参：处理结果
     */
    public List<IamMenu> listProfileMenus(Long userId) {
        List<IamMenu> menus = iamGateway.findVisibleMenusByUserId(userId);
        log.info("查询用户可见菜单: userId={}, count={}", userId, menus.size());
        return menus;
    }
}


