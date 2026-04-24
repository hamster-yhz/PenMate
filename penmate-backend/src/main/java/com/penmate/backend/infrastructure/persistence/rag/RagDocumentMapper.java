package com.penmate.backend.infrastructure.persistence.rag;

import com.penmate.backend.domain.rag.model.RagDocument;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * RagDocumentMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface RagDocumentMapper {

    @Select("""
            SELECT id, document_id, project_id, doc_type, title, source_ref, origin_object_key, origin_etag,
                   mime_type, parse_status, index_status, created_at, updated_at
            FROM rag_documents
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            ORDER BY id DESC
            """)
    List<RagDocument> findByProjectId(@Param("projectId") Long projectId);

    @Select("""
            SELECT id, document_id, project_id, doc_type, title, source_ref, origin_object_key, origin_etag,
                   mime_type, parse_status, index_status, created_at, updated_at
            FROM rag_documents
            WHERE project_id = #{projectId} AND document_id = #{docId} AND deleted_at IS NULL
            """)
    RagDocument findById(@Param("projectId") Long projectId, @Param("docId") Long docId);

    @Insert("""
            INSERT INTO rag_documents(document_id, project_id, doc_type, title, source_ref, origin_object_key, origin_etag,
                                      mime_type, parse_status, index_status)
            VALUES(#{documentId}, #{projectId}, #{docType}, #{title}, #{sourceRef}, #{originObjectKey}, #{originEtag},
                   #{mimeType}, #{parseStatus}, #{indexStatus})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RagDocument document);

    @Update("""
            UPDATE rag_documents
            SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND document_id = #{docId} AND deleted_at IS NULL
            """)
    int softDelete(@Param("projectId") Long projectId, @Param("docId") Long docId);

    @Update("""
            UPDATE rag_documents
            SET parse_status = #{parseStatus}, index_status = #{indexStatus}
            WHERE project_id = #{projectId} AND document_id = #{docId} AND deleted_at IS NULL
            """)
    int updateStatuses(@Param("projectId") Long projectId,
                       @Param("docId") Long docId,
                       @Param("parseStatus") String parseStatus,
                       @Param("indexStatus") String indexStatus);
}

