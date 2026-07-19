package com.penmate.backend.domain.model.model;

import lombok.Data;
import java.time.Instant;

@Data
/**
 * 模型服务提供商实体。
 */
public class ModelProvider {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 提供商业务 ID。 */
    private Long providerId;
    /** 提供商编码。 */
    private String code;
    /** 提供商名称。 */
    private String name;
    /** 提供商 API 基础地址。 */
    private String baseUrl;
    /** 认证类型。 */
    private String authType;
    /** 提供商状态。 */
    private String status;
    /** 创建时间。 */
    private Instant createdAt;
    /** 更新时间。 */
    private Instant updatedAt;

}

