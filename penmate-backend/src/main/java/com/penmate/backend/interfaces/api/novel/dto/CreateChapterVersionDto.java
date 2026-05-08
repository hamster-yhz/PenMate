package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class CreateChapterVersionDto {

    @NotBlank
    private String changeType;
    private String changeReason;

    @NotBlank
    private String createdBy;

}

