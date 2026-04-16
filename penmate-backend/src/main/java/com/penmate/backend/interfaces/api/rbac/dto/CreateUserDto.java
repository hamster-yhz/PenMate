package com.penmate.backend.interfaces.api.rbac.dto;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data

public class CreateUserDto {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String displayName;

    @NotNull
    private Integer status;

    private String authMethod;

}

