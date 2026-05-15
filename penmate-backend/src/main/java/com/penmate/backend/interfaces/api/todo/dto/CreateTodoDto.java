package com.penmate.backend.interfaces.api.todo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTodoDto {

    @NotBlank
    private String title;
    private String description;
    @NotBlank
    private String sourceType;
    @NotBlank
    private String todoStatus;
}
