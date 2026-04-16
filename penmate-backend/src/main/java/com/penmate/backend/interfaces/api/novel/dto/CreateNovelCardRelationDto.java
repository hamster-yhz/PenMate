package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data

public class CreateNovelCardRelationDto {
    @NotNull
    private Long fromCardId;
    @NotNull
    private Long toCardId;
    @NotBlank
    private String relationType;
    private String description;

}

