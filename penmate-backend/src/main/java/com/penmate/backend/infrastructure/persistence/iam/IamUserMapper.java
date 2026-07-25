package com.penmate.backend.infrastructure.persistence.iam;

import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * IamUserMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface IamUserMapper {

    @Select("""
            SELECT id, user_id, email, password_hash, display_name, bio, status, auth_method, last_login_at,
                   deletion_requested_at, deletion_due_at, rbac_revision
            FROM iam_users
            WHERE lower(email) = lower(#{email}) AND deleted_at IS NULL AND deletion_requested_at IS NULL
            """)
    IamUser findByEmail(@Param("email") String email);

    @Select("""
            SELECT id, user_id, email, password_hash, display_name, bio, status, auth_method, last_login_at,
                   deletion_requested_at, deletion_due_at, rbac_revision
            FROM iam_users
            WHERE user_id = #{userId} AND deleted_at IS NULL
            """)
    IamUser findByUserId(@Param("userId") Long userId);

    @Update("""
            UPDATE iam_users
            SET last_login_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId}
            """)
    int touchLastLoginByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT id, user_id, email, password_hash, display_name, bio, status, auth_method, last_login_at,
                   deletion_requested_at, deletion_due_at, rbac_revision
            FROM iam_users
            WHERE deleted_at IS NULL
            ORDER BY id DESC
            """)
    List<IamUser> findAll();

    @Select("""
            SELECT r.id, r.role_id, r.name, r.code, r.description, r.is_system, r.rbac_revision
            FROM iam_roles r
            JOIN iam_user_roles ur ON ur.role_id = r.role_id
            WHERE ur.user_id = #{userId} AND r.deleted_at IS NULL
            ORDER BY r.id DESC
            """)
    List<IamRole> findRolesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT DISTINCT p.id, p.permission_id, p.name, p.code, p.module, p.description
            FROM iam_permissions p
            JOIN iam_role_permissions rp ON rp.permission_id = p.permission_id
            JOIN iam_user_roles ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
            ORDER BY p.id DESC
            """)
    List<IamPermission> findPermissionsByUserId(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO iam_users(user_id, email, password_hash, display_name, bio, status, auth_method)
            VALUES(#{userId}, #{email}, #{passwordHash}, #{displayName}, #{bio}, #{status}, #{authMethod})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IamUser user);

    @Update("""
            UPDATE iam_users
            SET display_name = #{displayName},
                status = #{status}
            WHERE user_id = #{userId} AND deleted_at IS NULL
            """)
    int updateBasic(IamUser user);

    @Update("""
            UPDATE iam_users
            SET display_name = #{displayName}, bio = #{bio}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND deleted_at IS NULL
            """)
    int updateOwnProfile(IamUser user);

    @Update("""
            UPDATE iam_users
            SET email = #{email}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND deleted_at IS NULL
            """)
    int updateEmail(@Param("userId") Long userId, @Param("email") String email);

    @Update("""
            UPDATE iam_users
            SET password_hash = #{passwordHash}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND deleted_at IS NULL
            """)
    int updatePassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);

    @Update("""
            UPDATE iam_users
            SET status = 0, deletion_requested_at = #{requestedAt}, deletion_due_at = #{dueAt},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND deleted_at IS NULL AND deletion_requested_at IS NULL
            """)
    int requestDeletion(@Param("userId") Long userId,
                        @Param("requestedAt") java.time.Instant requestedAt,
                        @Param("dueAt") java.time.Instant dueAt);

    @Update("""
            UPDATE iam_users
            SET status = 1, deletion_requested_at = NULL, deletion_due_at = NULL,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND deleted_at IS NULL AND deletion_requested_at IS NOT NULL
            """)
    int restorePendingDeletion(@Param("userId") Long userId);

    @Select("""
            SELECT user_id FROM iam_users
            WHERE deletion_requested_at IS NOT NULL AND deletion_due_at <= #{now} AND deleted_at IS NULL
            ORDER BY deletion_due_at
            LIMIT 50
            """)
    List<Long> findDeletionDueUserIds(@Param("now") java.time.Instant now);

    @Update("""
            WITH target AS MATERIALIZED (
                SELECT user_id FROM iam_users
                WHERE user_id = #{userId} AND deletion_requested_at IS NOT NULL
                  AND deletion_due_at <= #{now} AND deleted_at IS NULL
            ),
            user_configs AS MATERIALIZED (
                SELECT model_config_id FROM model_configurations
                WHERE scope_type = 'USER' AND owner_user_id IN (SELECT user_id FROM target)
            ),
            d01 AS (DELETE FROM model_user_api_keys
                    WHERE user_id IN (SELECT user_id FROM target)
                       OR model_config_id IN (SELECT model_config_id FROM user_configs)),
            d02 AS (DELETE FROM model_user_preferences WHERE user_id IN (SELECT user_id FROM target)),
            d03 AS (DELETE FROM model_configurations WHERE model_config_id IN (SELECT model_config_id FROM user_configs)),
            d04 AS (DELETE FROM author_profiles WHERE user_id IN (SELECT user_id FROM target)),
            d05 AS (DELETE FROM user_ui_preferences WHERE user_id IN (SELECT user_id FROM target)),
            d06 AS (DELETE FROM auth_sessions WHERE user_id IN (SELECT user_id FROM target)),
            d07 AS (DELETE FROM iam_user_roles WHERE user_id IN (SELECT user_id FROM target))
            DELETE FROM iam_users WHERE user_id IN (SELECT user_id FROM target)
            """)
    int purgePendingDeletion(@Param("userId") Long userId, @Param("now") java.time.Instant now);

    @Update("""
            UPDATE iam_users
            SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND deleted_at IS NULL AND deletion_requested_at IS NULL
            """)
    int softDeleteByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT rbac_revision
            FROM iam_users
            WHERE user_id = #{userId} AND deleted_at IS NULL
            FOR UPDATE
            """)
    Long lockRbacRevision(@Param("userId") Long userId);

    @Delete("DELETE FROM iam_user_roles WHERE user_id = #{userId}")
    int deleteAllRoles(@Param("userId") Long userId);

    @Insert("""
            <script>
            INSERT INTO iam_user_roles(user_id, role_id)
            VALUES
            <foreach collection="roleIds" item="roleId" separator=",">
                (#{userId}, #{roleId})
            </foreach>
            </script>
            """)
    int insertRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    @Update("""
            UPDATE iam_users
            SET rbac_revision = rbac_revision + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND deleted_at IS NULL AND rbac_revision = #{expectedRevision}
            """)
    int incrementRbacRevision(@Param("userId") Long userId, @Param("expectedRevision") Long expectedRevision);
}
