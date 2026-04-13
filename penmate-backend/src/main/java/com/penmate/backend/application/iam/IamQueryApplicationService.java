package com.penmate.backend.application.iam;

import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamMenu;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IamQueryApplicationService {

    private final IamGateway iamGateway;

    public IamQueryApplicationService(IamGateway iamGateway) {
        this.iamGateway = iamGateway;
    }

    public List<IamUser> listUsers() {
        return iamGateway.findAllUsers();
    }

    public IamUser getUser(Long id) {
        IamUser user = iamGateway.findUserById(id);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        return user;
    }

    public List<IamRole> listRoles() {
        return iamGateway.findAllRoles();
    }

    public List<IamPermission> listPermissions() {
        return iamGateway.findAllPermissions();
    }

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

    public IamUser updateUser(Long id, String displayName, Integer status) {
        IamUser user = getUser(id);
        user.setDisplayName(displayName);
        user.setStatus(status);
        int affected = iamGateway.updateUserBasic(user);
        if (affected <= 0) {
            throw new IllegalArgumentException("User not found");
        }
        return getUser(id);
    }

    public void deleteUser(Long id) {
        int affected = iamGateway.softDeleteUser(id);
        if (affected <= 0) {
            throw new IllegalArgumentException("User not found");
        }
    }

    public IamRole createRole(String name, String code, String description, Boolean isSystem) {
        IamRole role = new IamRole();
        role.setName(name);
        role.setCode(code);
        role.setDescription(description);
        role.setIsSystem(Boolean.TRUE.equals(isSystem));
        iamGateway.insertRole(role);
        return role;
    }

    public IamRole updateRole(Long id, String name, String description) {
        IamRole role = iamGateway.findRoleById(id);
        if (role == null) {
            throw new IllegalArgumentException("Role not found");
        }
        role.setName(name);
        role.setDescription(description);
        int affected = iamGateway.updateRoleBasic(role);
        if (affected <= 0) {
            throw new IllegalArgumentException("Role not found");
        }
        return iamGateway.findRoleById(id);
    }

    public void deleteRole(Long id) {
        IamRole role = iamGateway.findRoleById(id);
        if (role == null) {
            throw new IllegalArgumentException("Role not found");
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new IllegalArgumentException("System role cannot be deleted");
        }
        int affected = iamGateway.softDeleteRole(id);
        if (affected <= 0) {
            throw new IllegalArgumentException("Role not found");
        }
    }

    public void assignRoleToUser(Long userId, Long roleId) {
        if (iamGateway.findUserById(userId) == null) {
            throw new IllegalArgumentException("User not found");
        }
        if (iamGateway.findRoleById(roleId) == null) {
            throw new IllegalArgumentException("Role not found");
        }
        iamGateway.assignRoleToUser(userId, roleId);
    }

    public void removeRoleFromUser(Long userId, Long roleId) {
        int affected = iamGateway.removeRoleFromUser(userId, roleId);
        if (affected <= 0) {
            throw new IllegalArgumentException("User role binding not found");
        }
    }

    public void assignPermissionToRole(Long roleId, Long permissionId) {
        if (iamGateway.findRoleById(roleId) == null) {
            throw new IllegalArgumentException("Role not found");
        }
        if (iamGateway.findPermissionById(permissionId) == null) {
            throw new IllegalArgumentException("Permission not found");
        }
        iamGateway.assignPermissionToRole(roleId, permissionId);
    }

    public void removePermissionFromRole(Long roleId, Long permissionId) {
        int affected = iamGateway.removePermissionFromRole(roleId, permissionId);
        if (affected <= 0) {
            throw new IllegalArgumentException("Role permission binding not found");
        }
    }

    public List<IamMenu> listMenus() {
        return iamGateway.findVisibleMenus();
    }

    public List<IamMenu> listProfileMenus(Long userId) {
        return iamGateway.findVisibleMenusByUserId(userId);
    }
}

