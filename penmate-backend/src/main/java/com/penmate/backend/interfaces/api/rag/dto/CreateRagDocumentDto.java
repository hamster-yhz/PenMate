package com.penmate.backend.interfaces.api.rag.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateRagDocumentDto {

    @NotBlank
    private String docType;

    @NotBlank
    private String title;

    private String sourceRef;

    @NotBlank
    private String originObjectKey;

    private String originEtag;

    private String mimeType;

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getOriginObjectKey() {
        return originObjectKey;
    }

    public void setOriginObjectKey(String originObjectKey) {
        this.originObjectKey = originObjectKey;
    }

    public String getOriginEtag() {
        return originEtag;
    }

    public void setOriginEtag(String originEtag) {
        this.originEtag = originEtag;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
}

