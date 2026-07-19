package com.penmate.backend.domain.iam.repository;

import com.penmate.backend.domain.iam.model.IamMenu;
import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;

import java.util.List;

public interface IamGateway {

    IamUser findUserByEmail(String email);

    IamUser findUserByUserId(Long userId);

    int touchLastLoginByUserId(Long userId);

    List<IamRole> findRolesByUserId(Long userId);

    List<IamPermission> findPermissionsByUserId(Long userId);

    List<IamUser> findAllUsers();

    int insertUser(IamUser user);

    int updateUserBasic(IamUser user);

    int updateOwnProfile(IamUser user);

    int updatePassword(Long userId, String passwordHash);

    int softDeleteUserByUserId(Long userId);

    int assignRoleToUser(Long userId, Long roleId);

    int removeRoleFromUser(Long userId, Long roleId);

    List<IamRole> findAllRoles();

    IamRole findRoleByRoleId(Long roleId);

    int insertRole(IamRole role);

    int updateRoleBasic(IamRole role);

    int softDeleteRoleByRoleId(Long roleId);

    int assignPermissionToRole(Long roleId, Long permissionId);

    int removePermissionFromRole(Long roleId, Long permissionId);

    List<IamPermission> findPermissionsByRoleId(Long roleId);

    List<IamPermission> findAllPermissions();

    IamPermission findPermissionByPermissionId(Long permissionId);

    List<IamMenu> findVisibleMenus();

    List<IamMenu> findVisibleMenusByUserId(Long userId);
}

