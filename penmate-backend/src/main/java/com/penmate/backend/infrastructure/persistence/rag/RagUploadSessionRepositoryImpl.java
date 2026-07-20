package com.penmate.backend.infrastructure.persistence.rag;

import com.penmate.backend.domain.rag.model.RagUploadSession;
import com.penmate.backend.domain.rag.repository.RagUploadSessionRepository;
import org.springframework.stereotype.Repository;

@Repository
public class RagUploadSessionRepositoryImpl implements RagUploadSessionRepository {
    private final RagUploadSessionMapper mapper;

    public RagUploadSessionRepositoryImpl(RagUploadSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public RagUploadSession findByIdForUpdate(Long uploadId) {
        return mapper.findByIdForUpdate(uploadId);
    }

    @Override
    public int insert(RagUploadSession session) {
        return mapper.insert(session);
    }

    @Override
    public int markCompleted(Long uploadId) {
        return mapper.markCompleted(uploadId);
    }

    @Override
    public int markRejected(Long uploadId) {
        return mapper.markRejected(uploadId);
    }
}
