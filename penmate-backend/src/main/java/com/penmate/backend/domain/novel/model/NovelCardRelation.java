package com.penmate.backend.domain.novel.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
@Data
/**
 * 小说卡片关系实体。
 */
public class NovelCardRelation {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 关系业务 ID。 */
    @JsonProperty("relationId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long cardRelationId;
    /** 所属项目业务 ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;
    /** 起始卡片业务 ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromCardId;
    /** 目标卡片业务 ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toCardId;
    /** 关系类型。 */
    private String relationType;
    /** 关系说明。 */
    private String description;

}

