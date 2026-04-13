package com.penmate.backend.interfaces.api.novel.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateNovelOutlineNodeDto {
    private Long parentId;

    @NotBlank
    private String title;

    private String nodeType;
    private Integer sortOrder;
    private String content;

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}

