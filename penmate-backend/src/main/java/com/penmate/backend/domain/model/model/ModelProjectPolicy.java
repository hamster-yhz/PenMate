package com.penmate.backend.domain.model.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
/**
 * 项目模型策略实体。
 */
public class ModelProjectPolicy {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 项目策略业务 ID。 */
    private Long projectPolicyId;
    /** 所属项目业务 ID。 */
    private Long projectId;
    /** 策略名称。 */
    private String policyName;
    /** 适用场景（如写作、总结、润色）。 */
    private String scene;
    /** 提供商模型业务 ID。 */
    private Long providerModelId;
    /** 调用模型名（可自定义字符串）。 */
    private String modelName;
    /** 覆盖调用基础地址（可选）；为空时回退供应商默认 baseUrl。 */
    private String baseUrl;
    /** 用户 API Key 业务 ID。 */
    private Long userKeyId;
    /** 官方 API Key 业务 ID。 */
    private Long officialKeyId;
    /** 温度参数。 */
    private BigDecimal temperature;
    /** Top-P 参数。 */
    private BigDecimal topP;
    /** 最大输出 Token 数。 */
    private Integer maxTokens;
    /** 兜底策略配置（JSON）。 */
    private String fallbackPolicyJson;
    /** 是否为默认策略。 */
    private Boolean isDefault;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除时间。 */
    private LocalDateTime deletedAt;

}

