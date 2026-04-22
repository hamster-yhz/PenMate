 package com.penmate.backend.infrastructure.persistence.iam;

import com.penmate.backend.domain.iam.model.IamMenu;
import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * IamGatewayImpl。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Repository
public class IamGatewayImpl implements IamGateway {

    private final IamUserMapper iamUserMapper;
    private final IamRoleMapper iamRoleMapper;
    private final IamPermissionMapper iamPermissionMapper;
    private final IamMenuMapper iamMenuMapper;

    public IamGatewayImpl(IamUserMapper iamUserMapper,
                          IamRoleMapper iamRoleMapper,
                          IamPermissionMapper iamPermissionMapper,
                          IamMenuMapper iamMenuMapper) {
        this.iamUserMapper = iamUserMapper;
        this.iamRoleMapper = iamRoleMapper;
        this.iamPermissionMapper = iamPermissionMapper;
        this.iamMenuMapper = iamMenuMapper;
    }

    /**
     * 处理业务请求。
     *
     * @param email 入参：email
     * @return 出参：处理结果
     */
    @Override
    public IamUser findUserByEmail(String email) {
        return iamUserMapper.findByEmail(email);
    }

    /**
     * 处理业务请求。
     *
     * @param id 入参：id
     * @return 出参：处理结果
     */
    @Override
    public IamUser findUserById(Long id) {
        return iamUserMapper.findById(id);
    }

    /**
     * 处理业务请求。
     *
     * @param id 入参：id
     * @return 出参：处理结果
     */
    @Override
    public int touchLastLogin(Long id) {
        return iamUserMapper.touchLastLogin(id);
    }

    /**
     * 处理业务请求。
     *
     * @param userId 入参：userId
     * @return 出参：处理结果
     */
    @Override
    public List<IamRole> findRolesByUserId(Long userId) {
        return iamUserMapper.findRolesByUserId(userId);
    }

    /**
     * 处理业务请求。
     *
     * @param userId 入参：userId
     * @return 出参：处理结果
     */
    @Override
    public List<IamPermission> findPermissionsByUserId(Long userId) {
        return iamUserMapper.findPermissionsByUserId(userId);
    }

    /**
     * 处理业务请求。
     *
     * @return 出参：处理结果
     */
    @Override
    public List<IamUser> findAllUsers() {
        return iamUserMapper.findAll();
    }

    /**
     * 处理业务请求。
     *
     * @param user 入参：user
     * @return 出参：处理结果
     */
    @Override
    public int insertUser(IamUser user) {
        return iamUserMapper.insert(user);
    }

    /**
     * 更新业务数据。
     *
     * @param user 入参：user
     * @return 出参：处理结果
     */
    @Override
    public int updateUserBasic(IamUser user) {
        return iamUserMapper.updateBasic(user);
    }

    /**
     * 处理业务请求。
     *
     * @param id 入参：id
     * @return 出参：处理结果
     */
    @Override
    public int softDeleteUser(Long id) {
        return iamUserMapper.softDelete(id);
    }

    /**
     * 处理业务请求。
     *
     * @param userId 入参：userId
     * @param roleId 入参：roleId
     * @return 出参：处理结果
     */
    @Override
    public int assignRoleToUser(Long userId, Long roleId) {
        return iamUserMapper.assignRole(userId, roleId);
    }

    /**
     * 移除业务数据。
     *
     * @param userId 入参：userId
     * @param roleId 入参：roleId
     * @return 出参：处理结果
     */
    @Override
    public int removeRoleFromUser(Long userId, Long roleId) {
        return iamUserMapper.removeRole(userId, roleId);
    }

    /**
     * 处理业务请求。
     *
     * @return 出参：处理结果
     */
    @Override
    public List<IamRole> findAllRoles() {
        return iamRoleMapper.findAll();
    }

    /**
     * 处理业务请求。
     *
     * @param id 入参：id
     * @return 出参：处理结果
     */
    @Override
    public IamRole findRoleById(Long id) {
        return iamRoleMapper.findById(id);
    }

    /**
     * 处理业务请求。
     *
     * @param role 入参：role
     * @return 出参：处理结果
     */
    @Override
    public int insertRole(IamRole role) {
        return iamRoleMapper.insert(role);
    }

    /**
     * 更新业务数据。
     *
     * @param role 入参：role
     * @return 出参：处理结果
     */
    @Override
    public int updateRoleBasic(IamRole role) {
        return iamRoleMapper.updateBasic(role);
    }

    /**
     * 处理业务请求。
     *
     * @param id 入参：id
     * @return 出参：处理结果
     */
    @Override
    public int softDeleteRole(Long id) {
        return iamRoleMapper.softDelete(id);
    }

    /**
     * 处理业务请求。
     *
     * @param roleId 入参：roleId
     * @param permissionId 入参：permissionId
     * @return 出参：处理结果
     */
    @Override
    public int assignPermissionToRole(Long roleId, Long permissionId) {
        return iamRoleMapper.assignPermission(roleId, permissionId);
    }

    /**
     * 移除业务数据。
     *
     * @param roleId 入参：roleId
     * @param permissionId 入参：permissionId
     * @return 出参：处理结果
     */
    @Override
    public int removePermissionFromRole(Long roleId, Long permissionId) {
        return iamRoleMapper.removePermission(roleId, permissionId);
    }

    /**
     * 处理业务请求。
     *
     * @return 出参：处理结果
     */
    @Override
    public List<IamPermission> findAllPermissions() {
        return iamPermissionMapper.findAll();
    }

    /**
     * 处理业务请求。
     *
     * @param id 入参：id
     * @return 出参：处理结果
     */
    @Override
    public IamPermission findPermissionById(Long id) {
        return iamPermissionMapper.findById(id);
    }

    /**
     * 处理业务请求。
     *
     * @return 出参：处理结果
     */
    @Override
    public List<IamMenu> findVisibleMenus() {
        return iamMenuMapper.findVisibleAll();
    }

    /**
     * 处理业务请求。
     *
     * @param userId 入参：userId
     * @return 出参：处理结果
     */
    @Override
    public List<IamMenu> findVisibleMenusByUserId(Long userId) {
        return iamMenuMapper.findVisibleByUserId(userId);
    }
}

