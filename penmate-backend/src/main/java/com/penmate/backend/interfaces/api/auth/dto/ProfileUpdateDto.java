package com.penmate.backend.interfaces.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateDto {
    @NotBlank
    @Size(max = 80)
    private String displayName;

    @Size(max = 500)
    private String bio;
}
