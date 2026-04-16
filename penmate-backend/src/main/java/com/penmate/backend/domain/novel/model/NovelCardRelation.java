package com.penmate.backend.domain.novel.model;

import lombok.Data;
@Data
public class NovelCardRelation {
    private Long id;
    private Long projectId;
    private Long fromCardId;
    private Long toCardId;
    private String relationType;
    private String description;

}

