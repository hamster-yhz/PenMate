package com.penmate.backend.interfaces.api.novel.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MoveNovelChapterDto {

    private String volumeId;

    @NotNull
    private Integer sortOrder;
}
