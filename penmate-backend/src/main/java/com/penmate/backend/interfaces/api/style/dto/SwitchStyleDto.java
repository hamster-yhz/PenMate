package com.penmate.backend.interfaces.api.style.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data

public class SwitchStyleDto {

    @NotNull
    private Long toStyleId;
    private Boolean warningConfirmed;
    private String reason;

}

