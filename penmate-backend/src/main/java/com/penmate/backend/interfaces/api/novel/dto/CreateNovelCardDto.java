package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class CreateNovelCardDto {
    @NotBlank
    private String cardType;
    @NotBlank
    private String name;
    private String summary;
    private String detailJson;

}

