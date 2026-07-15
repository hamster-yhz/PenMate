package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoryBibleAlias {
    private Long id;
    private Long aliasId;
    private Long storyBibleId;
    private Long nodeId;
    private String alias;
    private String normalizedAlias;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}
