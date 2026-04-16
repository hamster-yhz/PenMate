package com.penmate.backend.interfaces.api.rbac.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class CreateRoleDto {

    @NotBlank
    private String name;

    @NotBlank
    private String code;

    private String description;

    private Boolean isSystem;

}

