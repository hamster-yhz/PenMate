package com.penmate.backend.interfaces.api.rbac.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateRoleDto {

    @NotBlank
    private String name;

    @NotBlank
    private String code;

    private String description;

    private Boolean isSystem;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsSystem() {
        return isSystem;
    }

    public void setIsSystem(Boolean isSystem) {
        this.isSystem = isSystem;
    }
}

