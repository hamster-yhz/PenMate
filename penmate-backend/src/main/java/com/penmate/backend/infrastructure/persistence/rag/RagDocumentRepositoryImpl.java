package com.penmate.backend.infrastructure.persistence.rag;

import com.penmate.backend.domain.rag.model.RagDocument;
import com.penmate.backend.domain.rag.repository.RagDocumentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RagDocumentRepositoryImpl implements RagDocumentRepository {

    private final RagDocumentMapper ragDocumentMapper;

    public RagDocumentRepositoryImpl(RagDocumentMapper ragDocumentMapper) {
        this.ragDocumentMapper = ragDocumentMapper;
    }

    @Override
    public List<RagDocument> findByProjectId(Long projectId) {
        return ragDocumentMapper.findByProjectId(projectId);
    }

    @Override
    public RagDocument findById(Long projectId, Long docId) {
        return ragDocumentMapper.findById(projectId, docId);
    }

    @Override
    public int insert(RagDocument document) {
        return ragDocumentMapper.insert(document);
    }

    @Override
    public int softDelete(Long projectId, Long docId) {
        return ragDocumentMapper.softDelete(projectId, docId);
    }

    @Override
    public int updateStatuses(Long projectId, Long docId, String parseStatus, String indexStatus) {
        return ragDocumentMapper.updateStatuses(projectId, docId, parseStatus, indexStatus);
    }
}

