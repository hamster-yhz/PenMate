package com.penmate.backend.interfaces.api.rag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompleteRagUploadDto {
    @NotBlank
    private String uploadToken;
}
