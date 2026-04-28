package com.penmate.backend.domain.iam.repository;

import com.penmate.backend.domain.iam.model.IamMenu;
import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;

import java.util.List;

public interface IamGateway {

    IamUser findUserByEmail(String email);

    IamUser findUserById(Long id);

    int touchLastLogin(Long id);

    List<IamRole> findRolesByUserId(Long userId);

    List<IamPermission> findPermissionsByUserId(Long userId);

    List<IamUser> findAllUsers();

    int insertUser(IamUser user);

    int updateUserBasic(IamUser user);

    int softDeleteUser(Long id);

    int assignRoleToUser(Long userId, Long roleId);

    int removeRoleFromUser(Long userId, Long roleId);

    List<IamRole> findAllRoles();

    IamRole findRoleById(Long id);

    int insertRole(IamRole role);

    int updateRoleBasic(IamRole role);

    int softDeleteRole(Long id);

    int assignPermissionToRole(Long roleId, Long permissionId);

    int removePermissionFromRole(Long roleId, Long permissionId);

    List<IamPermission> findPermissionsByRoleId(Long roleId);

    List<IamPermission> findAllPermissions();

    IamPermission findPermissionById(Long id);

    List<IamMenu> findVisibleMenus();

    List<IamMenu> findVisibleMenusByUserId(Long userId);
}

