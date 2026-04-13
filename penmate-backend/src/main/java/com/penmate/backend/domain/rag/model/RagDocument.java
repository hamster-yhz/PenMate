package com.penmate.backend.domain.rag.model;

import java.time.LocalDateTime;

public class RagDocument {
    private Long id;
    private Long projectId;
    private String docType;
    private String title;
    private String sourceRef;
    private String originObjectKey;
    private String originEtag;
    private String mimeType;
    private String parseStatus;
    private String indexStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public String getOriginObjectKey() { return originObjectKey; }
    public void setOriginObjectKey(String originObjectKey) { this.originObjectKey = originObjectKey; }
    public String getOriginEtag() { return originEtag; }
    public void setOriginEtag(String originEtag) { this.originEtag = originEtag; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getParseStatus() { return parseStatus; }
    public void setParseStatus(String parseStatus) { this.parseStatus = parseStatus; }
    public String getIndexStatus() { return indexStatus; }
    public void setIndexStatus(String indexStatus) { this.indexStatus = indexStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

