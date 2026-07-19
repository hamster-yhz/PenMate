package com.penmate.backend.infrastructure.persistence.iam;

import com.penmate.backend.domain.iam.model.IamMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * IamMenuMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface IamMenuMapper {

    @Select("""
            SELECT id, menu_id, parent_id, title, path, sort_order, permission_code, visible
            FROM iam_menus
            WHERE deleted_at IS NULL AND visible = TRUE
            ORDER BY sort_order ASC, id ASC
            """)
    List<IamMenu> findVisibleAll();

    @Select("""
            SELECT m.id, m.menu_id, m.parent_id, m.title, m.path, m.sort_order, m.permission_code, m.visible
            FROM iam_menus m
            WHERE m.deleted_at IS NULL
              AND m.visible = TRUE
              AND (
                    m.permission_code IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM iam_user_roles ur
                        JOIN iam_role_permissions rp ON rp.role_id = ur.role_id
                        JOIN iam_permissions p ON p.permission_id = rp.permission_id
                        WHERE ur.user_id = #{userId}
                          AND p.code = m.permission_code
                    )
              )
            ORDER BY m.sort_order ASC, m.id ASC
            """)
    List<IamMenu> findVisibleByUserId(@Param("userId") Long userId);
}

