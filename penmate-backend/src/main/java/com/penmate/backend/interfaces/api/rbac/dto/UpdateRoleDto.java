package com.penmate.backend.interfaces.api.rbac.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class UpdateRoleDto {

    @NotBlank
    private String name;

    private String description;

}

