package com.penmate.backend.interfaces.api.novel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateNovelChapterDto {

    private Long volumeId;
    private Long outlineNodeId;

    @NotBlank
    private String title;

    @NotNull
    private Integer chapterNo;

    private Integer status;
    private Integer wordCount;
    private String excerpt;
    private String contentObjectKey;
    private String contentEtag;
    private Long contentSize;
    private String contentChecksum;
    private String storageProvider;

    public Long getVolumeId() { return volumeId; }
    public void setVolumeId(Long volumeId) { this.volumeId = volumeId; }
    public Long getOutlineNodeId() { return outlineNodeId; }
    public void setOutlineNodeId(Long outlineNodeId) { this.outlineNodeId = outlineNodeId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getChapterNo() { return chapterNo; }
    public void setChapterNo(Integer chapterNo) { this.chapterNo = chapterNo; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }
    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
    public String getContentObjectKey() { return contentObjectKey; }
    public void setContentObjectKey(String contentObjectKey) { this.contentObjectKey = contentObjectKey; }
    public String getContentEtag() { return contentEtag; }
    public void setContentEtag(String contentEtag) { this.contentEtag = contentEtag; }
    public Long getContentSize() { return contentSize; }
    public void setContentSize(Long contentSize) { this.contentSize = contentSize; }
    public String getContentChecksum() { return contentChecksum; }
    public void setContentChecksum(String contentChecksum) { this.contentChecksum = contentChecksum; }
    public String getStorageProvider() { return storageProvider; }
    public void setStorageProvider(String storageProvider) { this.storageProvider = storageProvider; }
}

