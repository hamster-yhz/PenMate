package com.penmate.backend.domain.ops.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class OpsAsyncJob {
    private Long id;
    private String jobType;
    private String bizKey;
    private String status;
    private String errorMsg;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}

