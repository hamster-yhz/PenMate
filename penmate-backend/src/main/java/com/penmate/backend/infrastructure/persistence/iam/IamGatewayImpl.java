 package com.penmate.backend.infrastructure.persistence.iam;

import com.penmate.backend.domain.iam.model.IamMenu;
import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamSession;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class IamGatewayImpl implements IamGateway {

    private final IamUserMapper iamUserMapper;
    private final IamSessionMapper iamSessionMapper;
    private final IamRoleMapper iamRoleMapper;
    private final IamPermissionMapper iamPermissionMapper;
    private final IamMenuMapper iamMenuMapper;

    public IamGatewayImpl(IamUserMapper iamUserMapper,
                          IamSessionMapper iamSessionMapper,
                          IamRoleMapper iamRoleMapper,
                          IamPermissionMapper iamPermissionMapper,
                          IamMenuMapper iamMenuMapper) {
        this.iamUserMapper = iamUserMapper;
        this.iamSessionMapper = iamSessionMapper;
        this.iamRoleMapper = iamRoleMapper;
        this.iamPermissionMapper = iamPermissionMapper;
        this.iamMenuMapper = iamMenuMapper;
    }

    @Override
    public IamUser findUserByEmail(String email) {
        return iamUserMapper.findByEmail(email);
    }

    @Override
    public IamUser findUserById(Long id) {
        return iamUserMapper.findById(id);
    }

    @Override
    public int touchLastLogin(Long id) {
        return iamUserMapper.touchLastLogin(id);
    }

    @Override
    public List<IamRole> findRolesByUserId(Long userId) {
        return iamUserMapper.findRolesByUserId(userId);
    }

    @Override
    public List<IamPermission> findPermissionsByUserId(Long userId) {
        return iamUserMapper.findPermissionsByUserId(userId);
    }

    @Override
    public int insertSession(IamSession session) {
        return iamSessionMapper.insert(session);
    }

    @Override
    public IamSession findSessionByAccessToken(String accessToken) {
        return iamSessionMapper.findByAccessToken(accessToken);
    }

    @Override
    public IamSession findSessionByRefreshToken(String refreshToken) {
        return iamSessionMapper.findByRefreshToken(refreshToken);
    }

    @Override
    public int revokeByAccessToken(String accessToken) {
        return iamSessionMapper.revokeByAccessToken(accessToken);
    }

    @Override
    public int rotateSession(IamSession session) {
        return iamSessionMapper.rotate(session);
    }

    @Override
    public List<IamUser> findAllUsers() {
        return iamUserMapper.findAll();
    }

    @Override
    public int insertUser(IamUser user) {
        return iamUserMapper.insert(user);
    }

    @Override
    public int updateUserBasic(IamUser user) {
        return iamUserMapper.updateBasic(user);
    }

    @Override
    public int softDeleteUser(Long id) {
        return iamUserMapper.softDelete(id);
    }

    @Override
    public int assignRoleToUser(Long userId, Long roleId) {
        return iamUserMapper.assignRole(userId, roleId);
    }

    @Override
    public int removeRoleFromUser(Long userId, Long roleId) {
        return iamUserMapper.removeRole(userId, roleId);
    }

    @Override
    public List<IamRole> findAllRoles() {
        return iamRoleMapper.findAll();
    }

    @Override
    public IamRole findRoleById(Long id) {
        return iamRoleMapper.findById(id);
    }

    @Override
    public int insertRole(IamRole role) {
        return iamRoleMapper.insert(role);
    }

    @Override
    public int updateRoleBasic(IamRole role) {
        return iamRoleMapper.updateBasic(role);
    }

    @Override
    public int softDeleteRole(Long id) {
        return iamRoleMapper.softDelete(id);
    }

    @Override
    public int assignPermissionToRole(Long roleId, Long permissionId) {
        return iamRoleMapper.assignPermission(roleId, permissionId);
    }

    @Override
    public int removePermissionFromRole(Long roleId, Long permissionId) {
        return iamRoleMapper.removePermission(roleId, permissionId);
    }

    @Override
    public List<IamPermission> findAllPermissions() {
        return iamPermissionMapper.findAll();
    }

    @Override
    public IamPermission findPermissionById(Long id) {
        return iamPermissionMapper.findById(id);
    }

    @Override
    public List<IamMenu> findVisibleMenus() {
        return iamMenuMapper.findVisibleAll();
    }

    @Override
    public List<IamMenu> findVisibleMenusByUserId(Long userId) {
        return iamMenuMapper.findVisibleByUserId(userId);
    }
}

