package com.penmate.backend.interfaces.api.agent.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class CreateAgentMessageDto {

    @NotBlank
    private String role;
    private String userMessageType;
    @NotBlank
    private String contentMd;
    private String attachmentsJson;
    private String toolCallsJson;

}

