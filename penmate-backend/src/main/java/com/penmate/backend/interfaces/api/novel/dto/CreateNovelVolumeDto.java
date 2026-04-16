package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class CreateNovelVolumeDto {

    @NotBlank
    private String title;

    private Integer sortOrder;

    private String description;

}

