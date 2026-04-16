package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class UpdateNovelOutlineNodeDto {
    private Long parentId;

    @NotBlank
    private String title;

    private String nodeType;
    private Integer sortOrder;
    private String content;

}

