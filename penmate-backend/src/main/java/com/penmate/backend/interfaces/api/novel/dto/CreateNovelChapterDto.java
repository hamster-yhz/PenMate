package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data

public class CreateNovelChapterDto {

    @NotBlank
    private String volumeId;

    @NotBlank
    private String title;

    @NotNull
    private Integer sortOrder;

}

