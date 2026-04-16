package com.penmate.backend.domain.plugin.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class PluginCatalogItem {
    private Long id;
    private String code;
    private String name;
    private String category;
    private String provider;
    private String status;
    private String latestVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}

