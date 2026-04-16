package com.penmate.backend.domain.rag.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

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

}

