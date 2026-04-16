package com.penmate.backend.interfaces.api.style.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class UpdateStyleDto {

    @NotBlank
    private String name;
    private String pace;
    private String tone;
    private String narrativeFocus;
    private String promptTemplate;
    private String sampleText;

}

