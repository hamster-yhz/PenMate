package com.penmate.backend.domain.novel.model;

import lombok.Data;
@Data
/**
 * 小说大纲节点实体。
 */
public class NovelOutlineNode {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 节点业务 ID。 */
    private Long outlineNodeId;
    /** 所属项目业务 ID。 */
    private Long projectId;
    /** 父节点业务 ID，根节点可为空。 */
    private Long parentId;
    /** 节点标题。 */
    private String title;
    /** 节点类型（卷、章、情节等）。 */
    private String nodeType;
    /** 同级排序序号。 */
    private Integer sortOrder;
    /** 节点内容描述。 */
    private String content;

}

