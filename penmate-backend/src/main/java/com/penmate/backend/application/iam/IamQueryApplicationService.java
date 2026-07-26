package com.penmate.backend.application.iam;

import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamMenu;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * IAM 查询与维护应用服务。
 * <p>负责用户、角色、权限、菜单的查询与基础维护，以及用户-角色、角色-权限关系绑定管理。</p>
 */
@Service
@Slf4j
public class IamQueryApplicationService {

    private final IamGateway iamGateway;
    private final BusinessIdGenerator businessIdGenerator;
    private final PasswordEncoder passwordEncoder;
    private final AdminSafetyPolicy adminSafetyPolicy;
    private final AuthorizationChangeDispatcher authorizationChanges;

    public IamQueryApplicationService(IamGateway iamGateway,
                                      BusinessIdGenerator businessIdGenerator,
                                      PasswordEncoder passwordEncoder,
                                      AdminSafetyPolicy adminSafetyPolicy,
                                      AuthorizationChangeDispatcher authorizationChanges) {
        this.iamGateway = iamGateway;
        this.businessIdGenerator = businessIdGenerator;
        this.passwordEncoder = passwordEncoder;
        this.adminSafetyPolicy = adminSafetyPolicy;
        this.authorizationChanges = authorizationChanges;
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
        IamUser user = iamGateway.findUserByUserId(id);
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
     * 查询指定用户已绑定角色。
     *
     * @param userId 入参：userId
     * @return 出参：处理结果
     */
    public List<IamRole> listUserRoles(Long userId) {
        List<IamRole> roles = iamGateway.findRolesByUserId(userId);
        log.info("查询用户角色列表: userId={}, count={}", userId, roles.size());
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
     * 查询指定角色已绑定权限。
     *
     * @param roleId 入参：roleId
     * @return 出参：处理结果
     */
    public List<IamPermission> listRolePermissions(Long roleId) {
        List<IamPermission> permissions = iamGateway.findPermissionsByRoleId(roleId);
        log.info("查询角色权限列表: roleId={}, count={}", roleId, permissions.size());
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
    @Transactional
    public IamUser createUser(String email, String displayName, Integer status, String initialPassword) {
        IamRole defaultRole = iamGateway.findRoleByCode(SystemRoleCodes.USER);
        if (defaultRole == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.of(
                    "Default user role is unavailable");
        }
        log.info("创建本地用户: email={}, displayName={}, status={}", email, displayName, status);
        IamUser user = new IamUser();
        user.setUserId(businessIdGenerator.nextId());
        user.setEmail(email.trim().toLowerCase(java.util.Locale.ROOT));
        user.setDisplayName(displayName);
        user.setStatus(status);
        user.setAuthMethod("local");
        user.setPasswordHash(passwordEncoder.encode(initialPassword));
        iamGateway.insertUser(user);
        iamGateway.addUserRoles(user.getUserId(), List.of(defaultRole.getRoleId()));
        log.info("创建用户成功: userId={}, email={}", user.getUserId(), user.getEmail());
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
    @Transactional
    public IamUser updateUser(Long id, String displayName, Integer status, Long actorUserId) {
        log.info("更新用户: userId={}, displayName={}, status={}", id, displayName, status);
        IamUser user = getUser(id);
        boolean statusChanged = !java.util.Objects.equals(user.getStatus(), status);
        if (statusChanged && status != null && status != 1) {
            adminSafetyPolicy.requireAccountMutationAllowed(actorUserId, id);
        }
        user.setDisplayName(displayName);
        user.setStatus(status);
        int affected = iamGateway.updateUserBasic(user);
        if (affected <= 0) {
            log.warn("更新用户失败: userId={}, reason=not_found", id);
            throw com.penmate.backend.application.common.exception.BusinessException.of("User not found");
        }
        if (statusChanged) {
            authorizationChanges.revokeSessionsAfterCommit(List.of(id),
                    "user:%d:status:%d".formatted(id, System.nanoTime()), actorUserId);
        }
        log.info("更新用户成功: userId={}", id);
        return getUser(id);
    }

    /**
     * 软删除用户。
     *
     * @param id 入参：id
     */
    @Transactional
    public void deleteUser(Long id, Long actorUserId) {
        log.info("删除用户: userId={}", id);
        adminSafetyPolicy.requireAccountMutationAllowed(actorUserId, id);
        int affected = iamGateway.softDeleteUserByUserId(id);
        if (affected <= 0) {
            log.warn("删除用户失败: userId={}, reason=not_found", id);
            throw com.penmate.backend.application.common.exception.BusinessException.of("User not found");
        }
        authorizationChanges.revokeSessionsAfterCommit(List.of(id),
                "user:%d:delete:%d".formatted(id, System.nanoTime()), actorUserId);
        log.info("删除用户成功: userId={}", id);
    }

    public IamUser restorePendingDeletion(Long id) {
        if (iamGateway.restorePendingUserDeletion(id) != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.of(
                    "Pending account deletion not found");
        }
        return getUser(id);
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
        if (Boolean.TRUE.equals(isSystem)) {
            throw com.penmate.backend.application.common.exception.BusinessException.forbidden(
                    "System roles can only be created by the application baseline");
        }
        IamRole role = new IamRole();
        role.setRoleId(businessIdGenerator.nextId());
        role.setName(name);
        role.setCode(code);
        role.setDescription(description);
        role.setIsSystem(Boolean.TRUE.equals(isSystem));
        iamGateway.insertRole(role);
        log.info("创建角色成功: roleId={}, code={}", role.getRoleId(), role.getCode());
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
        IamRole role = iamGateway.findRoleByRoleId(id);
        if (role == null) {
            log.warn("更新角色失败: roleId={}, reason=not_found", id);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role not found");
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw com.penmate.backend.application.common.exception.BusinessException.forbidden(
                    "System roles cannot be modified");
        }
        role.setName(name);
        role.setDescription(description);
        int affected = iamGateway.updateRoleBasic(role);
        if (affected <= 0) {
            log.warn("更新角色失败: roleId={}, reason=not_found_after_update", id);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role not found");
        }
        log.info("更新角色成功: roleId={}", id);
        return iamGateway.findRoleByRoleId(id);
    }

    /**
     * 删除角色。
     *
     * @param id 入参：id
     */
    @Transactional
    public void deleteRole(Long id, Long actorUserId) {
        log.info("删除角色: roleId={}", id);
        IamRole role = iamGateway.findRoleByRoleId(id);
        if (role == null) {
            log.warn("删除角色失败: roleId={}, reason=not_found", id);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role not found");
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            log.warn("删除角色失败: roleId={}, reason=system_role", id);
            throw com.penmate.backend.application.common.exception.BusinessException.of("System role cannot be deleted");
        }
        List<Long> affectedUserIds = iamGateway.incrementAuthorizationVersionsByRoleId(id);
        int affected = iamGateway.softDeleteRoleByRoleId(id);
        if (affected <= 0) {
            log.warn("删除角色失败: roleId={}, reason=not_found_after_delete", id);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role not found");
        }
        iamGateway.deleteUserRoleAssignments(id);
        authorizationChanges.revokeSessionsAfterCommit(affectedUserIds,
                "role:%d:delete:%d".formatted(id, System.nanoTime()), actorUserId);
        log.info("删除角色成功: roleId={}", id);
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


