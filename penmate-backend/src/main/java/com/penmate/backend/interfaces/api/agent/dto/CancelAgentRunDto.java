package com.penmate.backend.interfaces.api.agent.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelAgentRunDto {

    @Size(max = 500, message = "reason must not exceed 500 characters")
    private String reason;
}
