package com.penmate.backend.interfaces.api.novel.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompleteNovelCoverUploadDto {
    @NotBlank
    private String uploadToken;
    @Valid @NotNull
    private NovelCoverCropDto crop;
}
