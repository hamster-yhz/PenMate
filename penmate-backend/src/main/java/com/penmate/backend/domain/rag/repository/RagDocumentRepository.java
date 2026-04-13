package com.penmate.backend.domain.rag.repository;

import com.penmate.backend.domain.rag.model.RagDocument;

import java.util.List;

public interface RagDocumentRepository {

    List<RagDocument> findByProjectId(Long projectId);

    RagDocument findById(Long projectId, Long docId);

    int insert(RagDocument document);

    int softDelete(Long projectId, Long docId);

    int updateStatuses(Long projectId, Long docId, String parseStatus, String indexStatus);
}

