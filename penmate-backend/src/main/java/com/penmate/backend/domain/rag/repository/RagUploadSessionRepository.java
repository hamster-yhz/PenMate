package com.penmate.backend.domain.rag.repository;

import com.penmate.backend.domain.rag.model.RagUploadSession;

public interface RagUploadSessionRepository {
    RagUploadSession findByIdForUpdate(Long uploadId);
    int insert(RagUploadSession session);
    int markCompleted(Long uploadId);
    int markRejected(Long uploadId);
}
