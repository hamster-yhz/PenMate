package com.penmate.backend.domain.iam.repository;

import com.penmate.backend.domain.iam.model.IamMenu;
import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.model.IamRbacAssignmentAudit;

import java.util.List;
import java.time.Instant;

public interface IamGateway {

    IamUser findUserByEmail(String email);

    IamUser findUserByUserId(Long userId);

    int touchLastLoginByUserId(Long userId);

    List<IamRole> findRolesByUserId(Long userId);

    List<IamPermission> findPermissionsByUserId(Long userId);

    Long findAuthorizationVersion(Long userId);

    List<IamUser> findAllUsers();

    int insertUser(IamUser user);

    int addUserRoles(Long userId, List<Long> roleIds);

    int updateUserBasic(IamUser user);

    int updateOwnProfile(IamUser user);

    int updateEmail(Long userId, String email);

    int updatePassword(Long userId, String passwordHash);

    int requestUserDeletion(Long userId, Instant requestedAt, Instant dueAt);

    int restorePendingUserDeletion(Long userId);

    List<Long> findDeletionDueUserIds(Instant now);

    int purgePendingUserDeletion(Long userId, Instant now);

    int softDeleteUserByUserId(Long userId);

    Long lockUserRbacRevision(Long userId);

    int replaceUserRoles(Long userId, List<Long> roleIds, Long expectedRevision);

    List<IamRole> findAllRoles();

    IamRole findRoleByRoleId(Long roleId);

    IamRole findRoleByCode(String code);

    List<Long> findUserIdsByRoleId(Long roleId);

    List<Long> incrementAuthorizationVersionsByRoleId(Long roleId);

    int countActiveUsersByRoleCode(String roleCode);

    int insertRole(IamRole role);

    int updateRoleBasic(IamRole role);

    int softDeleteRoleByRoleId(Long roleId);

    int deleteUserRoleAssignments(Long roleId);

    Long lockRoleRbacRevision(Long roleId);

    int replaceRolePermissions(Long roleId, List<Long> permissionIds, Long expectedRevision);

    List<IamPermission> findPermissionsByRoleId(Long roleId);

    List<IamPermission> findAllPermissions();

    IamPermission findPermissionByPermissionId(Long permissionId);

    void insertRbacAssignmentAudit(IamRbacAssignmentAudit audit);

    List<IamMenu> findVisibleMenus();

    List<IamMenu> findVisibleMenusByUserId(Long userId);
}

