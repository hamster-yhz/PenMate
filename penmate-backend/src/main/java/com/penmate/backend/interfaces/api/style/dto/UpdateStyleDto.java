package com.penmate.backend.interfaces.api.style.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateStyleDto {

    @NotBlank
    private String name;
    private String pace;
    private String tone;
    private String narrativeFocus;
    private String promptTemplate;
    private String sampleText;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPace() {
        return pace;
    }

    public void setPace(String pace) {
        this.pace = pace;
    }

    public String getTone() {
        return tone;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }

    public String getNarrativeFocus() {
        return narrativeFocus;
    }

    public void setNarrativeFocus(String narrativeFocus) {
        this.narrativeFocus = narrativeFocus;
    }

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(String promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    public String getSampleText() {
        return sampleText;
    }

    public void setSampleText(String sampleText) {
        this.sampleText = sampleText;
    }
}

