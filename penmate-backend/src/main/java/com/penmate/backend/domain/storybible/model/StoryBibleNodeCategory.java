package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoryBibleNodeCategory {
    private Long id;
    private Long storyBibleId;
    private Long nodeId;
    private Long categoryId;
    private LocalDateTime createdAt;
}
