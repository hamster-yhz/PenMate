package com.penmate.backend.domain.novel.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
@Data
/**
 * 小说卡片实体。
 */
public class NovelCard {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 卡片业务 ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long cardId;
    /** 所属项目业务 ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;
    /** 卡片类型（角色、地点、物品等）。 */
    private String cardType;
    /** 卡片名称。 */
    private String name;
    /** 卡片摘要。 */
    private String summary;
    /** 卡片结构化详情（JSON）。 */
    private String detailJson;

}

