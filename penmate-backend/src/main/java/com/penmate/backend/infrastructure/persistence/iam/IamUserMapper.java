package com.penmate.backend.infrastructure.persistence.iam;

import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
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
            SELECT id, user_id, email, password_hash, display_name, status, auth_method,
                   main_agent_model_config_id, dirty_work_agent_model_config_id, last_login_at
            FROM iam_users
            WHERE email = #{email} AND deleted_at IS NULL
            """)
    IamUser findByEmail(@Param("email") String email);

    @Select("""
            SELECT id, user_id, email, password_hash, display_name, status, auth_method,
                   main_agent_model_config_id, dirty_work_agent_model_config_id, last_login_at
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
            SELECT id, user_id, email, password_hash, display_name, status, auth_method,
                   main_agent_model_config_id, dirty_work_agent_model_config_id, last_login_at
            FROM iam_users
            WHERE deleted_at IS NULL
            ORDER BY id DESC
            """)
    List<IamUser> findAll();

    @Select("""
            SELECT r.id, r.role_id, r.name, r.code, r.description
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
            INSERT INTO iam_users(user_id, email, password_hash, display_name, status, auth_method,
                                  main_agent_model_config_id, dirty_work_agent_model_config_id)
            VALUES(#{userId}, #{email}, #{passwordHash}, #{displayName}, #{status}, #{authMethod},
                   #{mainAgentModelConfigId}, #{dirtyWorkAgentModelConfigId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IamUser user);

    @Update("""
            UPDATE iam_users
            SET display_name = #{displayName},
                status = #{status},
                main_agent_model_config_id = #{mainAgentModelConfigId},
                dirty_work_agent_model_config_id = #{dirtyWorkAgentModelConfigId}
            WHERE user_id = #{userId} AND deleted_at IS NULL
            """)
    int updateBasic(IamUser user);

    @Update("""
            UPDATE iam_users
            SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND deleted_at IS NULL
            """)
    int softDeleteByUserId(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO iam_user_roles(user_id, role_id)
            VALUES(#{userId}, #{roleId})
            """)
    int assignRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Update("""
            DELETE FROM iam_user_roles
            WHERE user_id = #{userId} AND role_id = #{roleId}
            """)
    int removeRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
