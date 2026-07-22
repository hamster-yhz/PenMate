package com.penmate.backend.interfaces.api.novel.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InitializeNovelCoverUploadDto {
    @NotBlank @Size(max = 255)
    private String filename;
    @NotBlank @Size(max = 100)
    private String mimeType;
    @NotNull @Min(1) @Max(10L * 1024L * 1024L)
    private Long size;
    @Size(min = 64, max = 64)
    private String sha256;
}
