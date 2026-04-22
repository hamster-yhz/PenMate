package com.penmate.backend.domain.iam.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * IAM 用户实体。
 */
public class IamUser {
    /** 用户主键 ID。 */
    private Long id;
    /** 登录邮箱。 */
    private String email;
    /** 密码哈希值。 */
    private String passwordHash;
    /** 展示昵称。 */
    private String displayName;
    /** 用户状态（启用/禁用）。 */
    private Integer status;
    /** 认证方式（密码、三方登录等）。 */
    private String authMethod;
    /** 最近一次登录时间。 */
    private LocalDateTime lastLoginAt;

}

