package com.penmate.backend.domain.agent.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class AgentGenerationTask {
    private Long id;
    private Long projectId;
    private Long conversationId;
    private Long chapterId;
    private String taskType;
    private String promptSnapshot;
    private String styleProfileSnapshot;
    private String pluginSnapshot;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMsg;
    private LocalDateTime createdAt;

}

