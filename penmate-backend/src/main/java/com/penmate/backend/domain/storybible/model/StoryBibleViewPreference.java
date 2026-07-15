package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoryBibleViewPreference {
    private Long id;
    private Long storyBibleId;
    private String viewCode;
    private String displayName;
    private Boolean hidden;
    private Integer sortOrder;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
