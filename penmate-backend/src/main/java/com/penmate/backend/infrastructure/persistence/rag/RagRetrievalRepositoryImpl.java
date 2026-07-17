package com.penmate.backend.infrastructure.persistence.rag;

import com.penmate.backend.domain.rag.model.RagRetrievalLog;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import com.penmate.backend.domain.rag.repository.RagRetrievalRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RagRetrievalRepositoryImpl implements RagRetrievalRepository {

    private final RagRetrievalMapper ragRetrievalMapper;

    public RagRetrievalRepositoryImpl(RagRetrievalMapper ragRetrievalMapper) {
        this.ragRetrievalMapper = ragRetrievalMapper;
    }

    @Override
    public List<RagRetrievedChunk> searchChunks(Long projectId, String query, int limit) {
        return ragRetrievalMapper.searchChunks(projectId, query, limit);
    }

    @Override
    public int insertRetrievalLog(RagRetrievalLog retrievalLog) {
        return ragRetrievalMapper.insertRetrievalLog(retrievalLog);
    }

    @Override
    public List<RagRetrievalLog> listRetrievalLogs(Long projectId) {
        return ragRetrievalMapper.listRetrievalLogs(projectId);
    }
}

