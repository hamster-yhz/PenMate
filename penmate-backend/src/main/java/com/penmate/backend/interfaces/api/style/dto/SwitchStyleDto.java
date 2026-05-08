package com.penmate.backend.interfaces.api.style.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class SwitchStyleDto {

    @NotBlank
    private String toStyleId;
    private Boolean warningConfirmed;
    private String reason;

}

