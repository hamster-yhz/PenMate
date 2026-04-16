package com.penmate.backend.domain.novel.model;

import lombok.Data;
@Data
public class NovelCard {
    private Long id;
    private Long projectId;
    private String cardType;
    private String name;
    private String summary;
    private String detailJson;

}

