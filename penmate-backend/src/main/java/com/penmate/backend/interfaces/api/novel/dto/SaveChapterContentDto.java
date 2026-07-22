package com.penmate.backend.interfaces.api.novel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveChapterContentDto {
    @NotBlank
    private String leaseToken;
    @NotNull
    private Long expectedRevision;
    @NotNull
    private String content;
}
