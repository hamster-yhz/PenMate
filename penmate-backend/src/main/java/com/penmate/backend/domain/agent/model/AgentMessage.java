package com.penmate.backend.domain.agent.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class AgentMessage {
    private Long id;
    private Long conversationId;
    private String role;
    private String userMessageType;
    private String contentMd;
    private String attachmentsJson;
    private String toolCallsJson;
    private Integer seqNo;
    private LocalDateTime createdAt;

}

