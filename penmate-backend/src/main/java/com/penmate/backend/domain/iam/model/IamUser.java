package com.penmate.backend.domain.iam.model;

import lombok.Data;
import java.time.Instant;

@Data
/**
 * IAM 用户实体。
 */
public class IamUser {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 用户业务 ID。 */
    private Long userId;
    /** 登录邮箱。 */
    private String email;
    /** 密码哈希值。 */
    private String passwordHash;
    /** 展示昵称。 */
    private String displayName;
    /** 个人简介。 */
    private String bio;
    /** 用户状态（启用/禁用）。 */
    private Integer status;
    /** 认证方式（密码、三方登录等）。 */
    private String authMethod;
    /** 主 Agent 当前模型配置 ID。 */
    private Long mainAgentModelConfigId;
    /** Dirty Work Agent 当前模型配置 ID。 */
    private Long dirtyWorkAgentModelConfigId;
    /** 最近一次登录时间。 */
    private Instant lastLoginAt;

}

