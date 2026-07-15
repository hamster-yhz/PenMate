package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoryBibleCategory {
    private Long id;
    private Long categoryId;
    private Long storyBibleId;
    private Long parentCategoryId;
    private String name;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
