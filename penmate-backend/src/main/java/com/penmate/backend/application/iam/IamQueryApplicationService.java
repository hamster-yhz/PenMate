package com.penmate.backend.application.iam;

import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamMenu;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * IamQueryApplicationService。
 * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
 */
@Service
public class IamQueryApplicationService {

    private final IamGateway iamGateway;

    public IamQueryApplicationService(IamGateway iamGateway) {
        this.iamGateway = iamGateway;
    }

    /**
     * 查询列表数据。
     *
     * @return 出参：处理结果
     */
    public List<IamUser> listUsers() {
        return iamGateway.findAllUsers();
    }

    /**
     * 查询详情数据。
     *
     * @param id 入参：id
     * @return 出参：处理结果
     */
    public IamUser getUser(Long id) {
        IamUser user = iamGateway.findUserById(id);
        if (user == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("User not found");
        }
        return user;
    }

    /**
     * 查询列表数据。
     *
     * @return 出参：处理结果
     */
    public List<IamRole> listRoles() {
        return iamGateway.findAllRoles();
    }

    /**
     * 查询列表数据。
     *
     * @return 出参：处理结果
     */
    public List<IamPermission> listPermissions() {
        return iamGateway.findAllPermissions();
    }

    /**
     * 创建业务数据。
     *
     * @param email 入参：email
     * @param displayName 入参：displayName
     * @param status 入参：status
     * @param authMethod 入参：authMethod
     * @return 出参：处理结果
     */
    public IamUser createUser(String email, String displayName, Integer status, String authMethod) {
        IamUser user = new IamUser();
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setStatus(status);
        user.setAuthMethod(authMethod == null || authMethod.isBlank() ? "local" : authMethod);
        user.setPasswordHash("{noop}changeme");
        iamGateway.insertUser(user);
        return user;
    }

    /**
     * 更新业务数据。
     *
     * @param id 入参：id
     * @param displayName 入参：displayName
     * @param status 入参：status
     * @return 出参：处理结果
     */
    public IamUser updateUser(Long id, String displayName, Integer status) {
        IamUser user = getUser(id);
        user.setDisplayName(displayName);
        user.setStatus(status);
        int affected = iamGateway.updateUserBasic(user);
        if (affected <= 0) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("User not found");
        }
        return getUser(id);
    }

    /**
     * 删除业务数据。
     *
     * @param id 入参：id
     */
    public void deleteUser(Long id) {
        int affected = iamGateway.softDeleteUser(id);
        if (affected <= 0) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("User not found");
        }
    }

    /**
     * 创建业务数据。
     *
     * @param name 入参：name
     * @param code 入参：code
     * @param description 入参：description
     * @param isSystem 入参：isSystem
     * @return 出参：处理结果
     */
    public IamRole createRole(String name, String code, String description, Boolean isSystem) {
        IamRole role = new IamRole();
        role.setName(name);
        role.setCode(code);
        role.setDescription(description);
        role.setIsSystem(Boolean.TRUE.equals(isSystem));
        iamGateway.insertRole(role);
        return role;
    }

    /**
     * 更新业务数据。
     *
     * @param id 入参：id
     * @param name 入参：name
     * @param description 入参：description
     * @return 出参：处理结果
     */
    public IamRole updateRole(Long id, String name, String description) {
        IamRole role = iamGateway.findRoleById(id);
        if (role == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role not found");
        }
        role.setName(name);
        role.setDescription(description);
        int affected = iamGateway.updateRoleBasic(role);
        if (affected <= 0) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role not found");
        }
        return iamGateway.findRoleById(id);
    }

    /**
     * 删除业务数据。
     *
     * @param id 入参：id
     */
    public void deleteRole(Long id) {
        IamRole role = iamGateway.findRoleById(id);
        if (role == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role not found");
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("System role cannot be deleted");
        }
        int affected = iamGateway.softDeleteRole(id);
        if (affected <= 0) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role not found");
        }
    }

    /**
     * 处理业务请求。
     *
     * @param userId 入参：userId
     * @param roleId 入参：roleId
     */
    public void assignRoleToUser(Long userId, Long roleId) {
        if (iamGateway.findUserById(userId) == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("User not found");
        }
        if (iamGateway.findRoleById(roleId) == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role not found");
        }
        iamGateway.assignRoleToUser(userId, roleId);
    }

    /**
     * 移除业务数据。
     *
     * @param userId 入参：userId
     * @param roleId 入参：roleId
     */
    public void removeRoleFromUser(Long userId, Long roleId) {
        int affected = iamGateway.removeRoleFromUser(userId, roleId);
        if (affected <= 0) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("User role binding not found");
        }
    }

    /**
     * 处理业务请求。
     *
     * @param roleId 入参：roleId
     * @param permissionId 入参：permissionId
     */
    public void assignPermissionToRole(Long roleId, Long permissionId) {
        if (iamGateway.findRoleById(roleId) == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role not found");
        }
        if (iamGateway.findPermissionById(permissionId) == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Permission not found");
        }
        iamGateway.assignPermissionToRole(roleId, permissionId);
    }

    /**
     * 移除业务数据。
     *
     * @param roleId 入参：roleId
     * @param permissionId 入参：permissionId
     */
    public void removePermissionFromRole(Long roleId, Long permissionId) {
        int affected = iamGateway.removePermissionFromRole(roleId, permissionId);
        if (affected <= 0) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Role permission binding not found");
        }
    }

    /**
     * 查询列表数据。
     *
     * @return 出参：处理结果
     */
    public List<IamMenu> listMenus() {
        return iamGateway.findVisibleMenus();
    }

    /**
     * 查询列表数据。
     *
     * @param userId 入参：userId
     * @return 出参：处理结果
     */
    public List<IamMenu> listProfileMenus(Long userId) {
        return iamGateway.findVisibleMenusByUserId(userId);
    }
}


