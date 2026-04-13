package com.penmate.backend.interfaces.api.novel.dto;

public class MoveNovelOutlineNodeDto {
    private Long parentId;
    private Integer sortOrder;

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}

