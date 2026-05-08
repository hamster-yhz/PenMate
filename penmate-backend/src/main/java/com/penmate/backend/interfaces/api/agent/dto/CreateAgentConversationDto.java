package com.penmate.backend.interfaces.api.agent.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data

public class CreateAgentConversationDto {

    @NotNull
    private String userId;
    private String title;
    private String status;

}

