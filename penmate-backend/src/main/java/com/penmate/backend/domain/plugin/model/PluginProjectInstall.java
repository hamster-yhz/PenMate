package com.penmate.backend.domain.plugin.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class PluginProjectInstall {
    private Long id;
    private Long projectId;
    private Long pluginId;
    private String pluginCode;
    private String pluginName;
    private String version;
    private String configJson;
    private Boolean enabled;
    private Long installedBy;
    private LocalDateTime installedAt;
    private LocalDateTime updatedAt;

}

