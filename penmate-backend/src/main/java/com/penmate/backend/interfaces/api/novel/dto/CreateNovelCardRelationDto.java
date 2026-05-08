package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class CreateNovelCardRelationDto {
    @NotBlank
    private String fromCardId;
    @NotBlank
    private String toCardId;
    @NotBlank
    private String relationType;
    private String description;

}

