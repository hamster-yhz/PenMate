package com.penmate.backend.infrastructure.persistence.rag;

import com.penmate.backend.domain.rag.model.RagDocument;
import com.penmate.backend.domain.rag.repository.RagDocumentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * RAG 文档仓储 MyBatis 实现。
 * <p>负责知识文档元数据的查询、新增、软删除与状态更新持久化操作。</p>
 */
@Repository
public class RagDocumentRepositoryImpl implements RagDocumentRepository {

    private final RagDocumentMapper ragDocumentMapper;

    public RagDocumentRepositoryImpl(RagDocumentMapper ragDocumentMapper) {
        this.ragDocumentMapper = ragDocumentMapper;
    }

    /**
     * 查询项目文档列表。
     * <p>流程：按项目ID读取文档元数据集合。</p>
     */
    @Override
    public List<RagDocument> findByProjectId(Long projectId) {
        return ragDocumentMapper.findByProjectId(projectId);
    }

    /**
     * 查询单个文档。
     * <p>流程：按项目与文档ID双键定位文档。</p>
     */
    @Override
    public RagDocument findById(Long projectId, Long docId) {
        return ragDocumentMapper.findById(projectId, docId);
    }

    /**
     * 新增文档记录。
     * <p>流程：将文档实体写入数据库。</p>
     */
    @Override
    public int insert(RagDocument document) {
        return ragDocumentMapper.insert(document);
    }

    /**
     * 软删除文档。
     * <p>流程：按项目与文档ID标记删除状态，不执行物理删除。</p>
     */
    @Override
    public int softDelete(Long projectId, Long docId) {
        return ragDocumentMapper.softDelete(projectId, docId);
    }

    /**
     * 更新文档解析/索引状态。
     * <p>流程：写入 parseStatus 与 indexStatus，供前端展示处理进度。</p>
     */
    @Override
    public int updateStatuses(Long projectId, Long docId, String parseStatus, String indexStatus) {
        return ragDocumentMapper.updateStatuses(projectId, docId, parseStatus, indexStatus);
    }

    @Override
    public int updateProcessingState(Long projectId, Long docId, String parseStatus, String indexStatus,
                                     String errorCode, String errorMessage) {
        return ragDocumentMapper.updateProcessingState(projectId, docId, parseStatus, indexStatus, errorCode, errorMessage);
    }
}

