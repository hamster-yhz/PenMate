package com.penmate.backend.interfaces.api.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class InitializeRagUploadDto {
    @NotBlank
    private String filename;
    private String title;
    @NotBlank
    private String mimeType;
    @NotNull
    @Positive
    private Long size;
    private String sha256;
}
