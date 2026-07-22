package com.penmate.backend.interfaces.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailChangeDto {
    @NotBlank
    private String currentPassword;

    @NotBlank
    @Email
    @Size(max = 190)
    private String newEmail;
}
