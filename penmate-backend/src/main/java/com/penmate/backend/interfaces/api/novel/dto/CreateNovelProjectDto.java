package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data

public class CreateNovelProjectDto {

    @NotNull
    private Long ownerUserId;

    @NotBlank
    private String title;

    private String summary;

    private Integer status;

}

