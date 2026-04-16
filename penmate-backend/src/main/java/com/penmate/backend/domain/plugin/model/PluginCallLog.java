package com.penmate.backend.domain.plugin.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class PluginCallLog {
    private Long id;
    private Long projectId;
    private String pluginCode;
    private String toolName;
    private String requestJson;
    private String responseJson;
    private Integer latencyMs;
    private String status;
    private String errorMsg;
    private LocalDateTime createdAt;

}

