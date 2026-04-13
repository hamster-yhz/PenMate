package com.penmate.backend.interfaces.api.novel.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateNovelCardDto {
    private String cardType;
    @NotBlank
    private String name;
    private String summary;
    private String detailJson;

    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
}

