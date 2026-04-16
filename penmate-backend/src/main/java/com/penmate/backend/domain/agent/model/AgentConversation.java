package com.penmate.backend.domain.agent.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class AgentConversation {
    private Long id;
    private Long projectId;
    private Long userId;
    private String title;
    private String contextScopeJson;
    private LocalDateTime lastMessageAt;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

}

