package com.penmate.backend.domain.model.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * 提供商模型定义实体。
 */
public class ModelProviderModel {
    /** 模型定义主键 ID。 */
    private Long id;
    /** 所属提供商 ID。 */
    private Long providerId;
    /** 提供商侧模型编码。 */
    private String modelCode;
    /** 模型展示名称。 */
    private String modelName;
    /** 上下文窗口上限。 */
    private Integer contextWindow;
    /** 单次最大输出 Token。 */
    private Integer maxOutput;
    /** 价格配置（JSON）。 */
    private String pricingJson;
    /** 模型状态。 */
    private String status;
    /** 创建时间。 */
    private LocalDateTime createdAt;

}

