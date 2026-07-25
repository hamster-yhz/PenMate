package com.penmate.backend.interfaces.api.agent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RetryAgentRunDto {

    @NotNull(message = "activeSkills must not be null")
    private List<String> activeSkills;
}
