package com.penmate.backend.domain.rag.repository;

import com.penmate.backend.domain.rag.model.RagRetrievalLog;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;

import java.util.List;

public interface RagRetrievalRepository {

    List<RagRetrievedChunk> searchChunks(Long projectId, String query, int limit);

    int insertRetrievalLog(RagRetrievalLog retrievalLog);

    List<RagRetrievalLog> listRetrievalLogs(Long projectId);
}

