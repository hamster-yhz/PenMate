package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class UpdateNovelVolumeDto {

    @NotBlank
    private String title;

    private Integer sortOrder;

    private String description;

}

