package com.penmate.backend.domain.rag.repository;

import com.penmate.backend.domain.rag.model.RagRetrievalLog;
import java.util.List;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;

public interface RagRetrievalRepository {

    /** Legacy test seam; production retrieval uses RagIndexRepository. */
    default List<RagRetrievedChunk> searchChunks(Long projectId, String query, int limit) {
        throw new UnsupportedOperationException("Lexical RAG retrieval was removed");
    }

    int insertRetrievalLog(RagRetrievalLog retrievalLog);

    List<RagRetrievalLog> listRetrievalLogs(Long projectId);
}

