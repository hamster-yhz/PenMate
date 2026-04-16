package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data

public class CreateChapterVersionDto {

    @NotBlank
    private String changeType;
    private String changeReason;

    @NotNull
    private Long createdBy;

}

