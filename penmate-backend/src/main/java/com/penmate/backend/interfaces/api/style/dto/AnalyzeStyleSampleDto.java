package com.penmate.backend.interfaces.api.style.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class AnalyzeStyleSampleDto {

    @NotBlank
    private String sampleText;

}

