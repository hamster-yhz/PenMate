package com.penmate.backend.infrastructure.persistence.rag;

import com.penmate.backend.domain.rag.model.RagDocument;
import com.penmate.backend.domain.rag.repository.RagDocumentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * RagDocumentRepositoryImpl。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Repository
public class RagDocumentRepositoryImpl implements RagDocumentRepository {

    private final RagDocumentMapper ragDocumentMapper;

    public RagDocumentRepositoryImpl(RagDocumentMapper ragDocumentMapper) {
        this.ragDocumentMapper = ragDocumentMapper;
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    @Override
    public List<RagDocument> findByProjectId(Long projectId) {
        return ragDocumentMapper.findByProjectId(projectId);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param docId 入参：docId
     * @return 出参：处理结果
     */
    @Override
    public RagDocument findById(Long projectId, Long docId) {
        return ragDocumentMapper.findById(projectId, docId);
    }

    /**
     * 处理业务请求。
     *
     * @param document 入参：document
     * @return 出参：处理结果
     */
    @Override
    public int insert(RagDocument document) {
        return ragDocumentMapper.insert(document);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param docId 入参：docId
     * @return 出参：处理结果
     */
    @Override
    public int softDelete(Long projectId, Long docId) {
        return ragDocumentMapper.softDelete(projectId, docId);
    }

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param docId 入参：docId
     * @param parseStatus 入参：parseStatus
     * @param indexStatus 入参：indexStatus
     * @return 出参：处理结果
     */
    @Override
    public int updateStatuses(Long projectId, Long docId, String parseStatus, String indexStatus) {
        return ragDocumentMapper.updateStatuses(projectId, docId, parseStatus, indexStatus);
    }
}

