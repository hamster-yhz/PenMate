package com.penmate.backend.interfaces.api.style.dto;

import jakarta.validation.constraints.NotBlank;

public class AnalyzeStyleSampleDto {

    @NotBlank
    private String sampleText;

    public String getSampleText() {
        return sampleText;
    }

    public void setSampleText(String sampleText) {
        this.sampleText = sampleText;
    }
}

