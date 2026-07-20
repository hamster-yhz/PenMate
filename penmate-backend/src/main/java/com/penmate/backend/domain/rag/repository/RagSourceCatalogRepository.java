package com.penmate.backend.domain.rag.repository;

import com.penmate.backend.domain.rag.model.RagSourceContent;

import java.util.List;

public interface RagSourceCatalogRepository {
    List<RagSourceContent> listProjectSources(Long projectId);
    RagSourceContent findKnowledgeDocument(Long projectId, Long documentId);
}
