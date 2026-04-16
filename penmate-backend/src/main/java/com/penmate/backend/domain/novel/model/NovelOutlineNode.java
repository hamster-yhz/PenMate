package com.penmate.backend.domain.novel.model;

import lombok.Data;
@Data
public class NovelOutlineNode {
    private Long id;
    private Long projectId;
    private Long parentId;
    private String title;
    private String nodeType;
    private Integer sortOrder;
    private String content;

}

