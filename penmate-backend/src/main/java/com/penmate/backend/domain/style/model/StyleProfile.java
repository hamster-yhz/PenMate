package com.penmate.backend.domain.style.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class StyleProfile {
    private Long id;
    private Long projectId;
    private String name;
    private Boolean isDefault;
    private String pace;
    private String tone;
    private String narrativeFocus;
    private String promptTemplate;
    private String sampleText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

}

