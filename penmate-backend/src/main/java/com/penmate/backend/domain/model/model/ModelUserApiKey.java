package com.penmate.backend.domain.model.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * 用户模型密钥实体。
 */
public class ModelUserApiKey {
    /** 密钥主键 ID。 */
    private Long id;
    /** 用户 ID。 */
    private Long userId;
    /** 提供商 ID。 */
    private Long providerId;
    /** 密钥展示名称。 */
    private String keyName;
    /** 加密后的 API Key。 */
    private String encryptedApiKey;
    /** 脱敏后的 API Key。 */
    private String maskedApiKey;
    /** 是否默认密钥。 */
    private Boolean isDefault;
    /** 最近使用时间。 */
    private LocalDateTime lastUsedAt;
    /** 密钥状态。 */
    private String status;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除时间。 */
    private LocalDateTime deletedAt;

}
