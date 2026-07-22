package com.penmate.backend.infrastructure.persistence.iam;

import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRole;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
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
            SELECT id, role_id, name, code, description, is_system, rbac_revision
            FROM iam_roles
            WHERE deleted_at IS NULL
            ORDER BY id DESC
            """)
    List<IamRole> findAll();

    @Select("""
            SELECT id, role_id, name, code, description, is_system, rbac_revision
            FROM iam_roles
            WHERE role_id = #{roleId} AND deleted_at IS NULL
            """)
    IamRole findByRoleId(@Param("roleId") Long roleId);

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
            WHERE role_id = #{roleId} AND deleted_at IS NULL
            """)
    int softDeleteByRoleId(@Param("roleId") Long roleId);

    @Select("""
            SELECT rbac_revision
            FROM iam_roles
            WHERE role_id = #{roleId} AND deleted_at IS NULL
            FOR UPDATE
            """)
    Long lockRbacRevision(@Param("roleId") Long roleId);

    @Delete("DELETE FROM iam_role_permissions WHERE role_id = #{roleId}")
    int deleteAllPermissions(@Param("roleId") Long roleId);

    @Insert("""
            <script>
            INSERT INTO iam_role_permissions(role_id, permission_id)
            VALUES
            <foreach collection="permissionIds" item="permissionId" separator=",">
                (#{roleId}, #{permissionId})
            </foreach>
            </script>
            """)
    int insertPermissions(@Param("roleId") Long roleId,
                          @Param("permissionIds") List<Long> permissionIds);

    @Update("""
            UPDATE iam_roles
            SET rbac_revision = rbac_revision + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE role_id = #{roleId} AND deleted_at IS NULL AND rbac_revision = #{expectedRevision}
            """)
    int incrementRbacRevision(@Param("roleId") Long roleId,
                              @Param("expectedRevision") Long expectedRevision);
}

