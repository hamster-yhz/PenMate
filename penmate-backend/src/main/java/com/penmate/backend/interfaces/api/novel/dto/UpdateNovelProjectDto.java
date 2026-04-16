package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class UpdateNovelProjectDto {

    @NotBlank
    private String title;

    private String summary;

    private Integer status;

}

