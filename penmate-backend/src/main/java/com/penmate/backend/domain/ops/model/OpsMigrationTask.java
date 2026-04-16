package com.penmate.backend.domain.ops.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class OpsMigrationTask {
    private Long id;
    private String migrationType;
    private String status;
    private Integer progressPct;
    private String summaryJson;
    private String errorMsg;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}

