package com.penmate.backend.infrastructure.persistence.iam;

import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRole;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * IamRoleMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface IamRoleMapper {

    @Select("""
            SELECT id, role_id, name, code, description, is_system
            FROM iam_roles
            WHERE deleted_at IS NULL
            ORDER BY id DESC
            """)
    List<IamRole> findAll();

    @Select("""
            SELECT id, role_id, name, code, description, is_system
            FROM iam_roles
            WHERE role_id = #{id} AND deleted_at IS NULL
            """)
    IamRole findById(@Param("id") Long id);

    @Select("""
            SELECT p.id, p.permission_id, p.name, p.code, p.module, p.description
            FROM iam_permissions p
            JOIN iam_role_permissions rp ON rp.permission_id = p.permission_id
            WHERE rp.role_id = #{roleId}
            ORDER BY p.id DESC
            """)
    List<IamPermission> findPermissionsByRoleId(@Param("roleId") Long roleId);

    @Insert("""
            INSERT INTO iam_roles(role_id, name, code, description, is_system)
            VALUES(#{roleId}, #{name}, #{code}, #{description}, #{isSystem})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IamRole role);

    @Update("""
            UPDATE iam_roles
            SET name = #{name}, description = #{description}
            WHERE role_id = #{roleId} AND deleted_at IS NULL
            """)
    int updateBasic(IamRole role);

    @Update("""
            UPDATE iam_roles
            SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE role_id = #{id} AND deleted_at IS NULL
            """)
    int softDelete(@Param("id") Long id);

    @Insert("""
            INSERT INTO iam_role_permissions(role_id, permission_id)
            VALUES(#{roleId}, #{permissionId})
            """)
    int assignPermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Update("""
            DELETE FROM iam_role_permissions
            WHERE role_id = #{roleId} AND permission_id = #{permissionId}
            """)
    int removePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}

