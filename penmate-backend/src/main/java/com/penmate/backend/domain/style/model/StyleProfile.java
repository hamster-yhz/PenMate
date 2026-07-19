package com.penmate.backend.domain.style.model;

import lombok.Data;
import java.time.Instant;

@Data
/**
 * 写作风格档案实体。
 */
public class StyleProfile {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 风格业务 ID。 */
    private Long styleId;
    /** 所属项目业务 ID。 */
    private Long projectId;
    /** 风格名称。 */
    private String name;
    /** 是否为项目默认风格。 */
    private Boolean isDefault;
    /** 写作节奏设定。 */
    private String pace;
    /** 语气风格设定。 */
    private String tone;
    /** 叙事焦点设定。 */
    private String narrativeFocus;
    /** 提示词模板。 */
    private String promptTemplate;
    /** 示例文本。 */
    private String sampleText;
    /** 创建时间。 */
    private Instant createdAt;
    /** 更新时间。 */
    private Instant updatedAt;
    /** 逻辑删除时间。 */
    private Instant deletedAt;

}

