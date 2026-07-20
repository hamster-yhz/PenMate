package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class CreateNovelProjectDto {

    @NotBlank
    private String title;

    private String summary;

    private Integer status;

}

