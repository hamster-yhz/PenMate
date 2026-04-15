package com.penmate.backend.infrastructure.persistence.iam;

import com.penmate.backend.domain.iam.model.IamPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * IamPermissionMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface IamPermissionMapper {

    @Select("""
            SELECT id, name, code, module, description
            FROM iam_permissions
            ORDER BY id DESC
            """)
    List<IamPermission> findAll();

    @Select("""
            SELECT id, name, code, module, description
            FROM iam_permissions
            WHERE id = #{id}
            """)
    IamPermission findById(@Param("id") Long id);
}

