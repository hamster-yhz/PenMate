package com.penmate.backend.interfaces.api.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeleteAccountDto {
    @NotBlank @Size(max = 200)
    private String currentPassword;

    @AssertTrue(message = "Account deletion must be explicitly confirmed")
    private boolean confirmed;
}
