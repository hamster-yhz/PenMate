package com.penmate.backend.interfaces.api.novel.dto;

import jakarta.validation.constraints.NotBlank;

public class CommitChapterContentDto {

    @NotBlank
    private String objectKey;
    private String etag;
    private Long size;
    private String checksum;
    private String storageProvider;

    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getStorageProvider() { return storageProvider; }
    public void setStorageProvider(String storageProvider) { this.storageProvider = storageProvider; }
}

