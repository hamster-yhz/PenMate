package com.penmate.backend.domain.novel.model;

import lombok.Data;
@Data
/**
 * 小说卡片关系实体。
 */
public class NovelCardRelation {
    /** 关系主键 ID。 */
    private Long id;
    /** 所属项目 ID。 */
    private Long projectId;
    /** 起始卡片 ID。 */
    private Long fromCardId;
    /** 目标卡片 ID。 */
    private Long toCardId;
    /** 关系类型。 */
    private String relationType;
    /** 关系说明。 */
    private String description;

}

