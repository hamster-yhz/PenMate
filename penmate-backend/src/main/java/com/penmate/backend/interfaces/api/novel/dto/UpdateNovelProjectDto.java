package com.penmate.backend.interfaces.api.novel.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateNovelProjectDto {

    @NotBlank
    private String title;

    private String summary;

    private Integer status;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

