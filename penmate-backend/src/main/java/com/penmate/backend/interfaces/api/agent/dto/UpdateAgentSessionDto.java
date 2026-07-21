package com.penmate.backend.interfaces.api.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAgentSessionDto {
    @NotBlank
    @Size(max = 80)
    private String title;
}
