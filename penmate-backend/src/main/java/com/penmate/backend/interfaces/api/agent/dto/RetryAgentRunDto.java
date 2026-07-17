package com.penmate.backend.interfaces.api.agent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RetryAgentRunDto {

    @NotNull(message = "operatorId must not be null")
    private String operatorId;
}
