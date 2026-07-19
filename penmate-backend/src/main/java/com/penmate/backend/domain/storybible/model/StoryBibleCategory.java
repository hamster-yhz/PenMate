package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.Instant;

@Data
public class StoryBibleCategory {
    private Long id;
    private Long categoryId;
    private Long storyBibleId;
    private Long parentCategoryId;
    private String name;
    private Integer sortOrder;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}
