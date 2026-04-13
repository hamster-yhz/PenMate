package com.penmate.backend.interfaces.api.novel.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateNovelVolumeDto {

    @NotBlank
    private String title;

    private Integer sortOrder;

    private String description;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

