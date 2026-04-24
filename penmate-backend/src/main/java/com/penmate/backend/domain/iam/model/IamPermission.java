package com.penmate.backend.domain.iam.model;

import lombok.Data;
@Data
/**
 * RBAC 权限点实体。
 */
public class IamPermission {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 权限业务 ID。 */
    private Long permissionId;
    /** 权限名称。 */
    private String name;
    /** 权限唯一编码。 */
    private String code;
    /** 权限所属业务模块。 */
    private String module;
    /** 权限说明。 */
    private String description;

}

