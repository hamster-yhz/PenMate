package com.penmate.backend.domain.iam.model;

import lombok.Data;
@Data
/**
 * RBAC 菜单实体。
 */
public class IamMenu {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 菜单业务 ID。 */
    private Long menuId;
    /** 父级菜单业务 ID，根节点可为空。 */
    private Long parentId;
    /** 菜单标题。 */
    private String title;
    /** 前端路由路径。 */
    private String path;
    /** 同级排序序号。 */
    private Integer sortOrder;
    /** 访问该菜单所需的权限编码。 */
    private String permissionCode;
    /** 是否在菜单中可见。 */
    private Boolean visible;

}

