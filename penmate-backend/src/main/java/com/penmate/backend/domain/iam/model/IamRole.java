package com.penmate.backend.domain.iam.model;

import lombok.Data;
@Data
/**
 * RBAC 角色实体。
 */
public class IamRole {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 角色业务 ID。 */
    private Long roleId;
    /** 角色名称。 */
    private String name;
    /** 角色唯一编码。 */
    private String code;
    /** 角色说明。 */
    private String description;
    /** 是否为系统内置角色。 */
    private Boolean isSystem;
    /** 角色权限关系的乐观并发修订号。 */
    private Long rbacRevision;

}

