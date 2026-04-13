package com.penmate.backend.infrastructure.persistence.rag;

import com.penmate.backend.domain.rag.model.RagDocument;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RagDocumentMapper {

    @Select("""
            SELECT id, project_id, doc_type, title, source_ref, origin_object_key, origin_etag,
                   mime_type, parse_status, index_status, created_at, updated_at
            FROM rag_documents
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            ORDER BY id DESC
            """)
    List<RagDocument> findByProjectId(@Param("projectId") Long projectId);

    @Select("""
            SELECT id, project_id, doc_type, title, source_ref, origin_object_key, origin_etag,
                   mime_type, parse_status, index_status, created_at, updated_at
            FROM rag_documents
            WHERE project_id = #{projectId} AND id = #{docId} AND deleted_at IS NULL
            """)
    RagDocument findById(@Param("projectId") Long projectId, @Param("docId") Long docId);

    @Insert("""
            INSERT INTO rag_documents(project_id, doc_type, title, source_ref, origin_object_key, origin_etag,
                                      mime_type, parse_status, index_status)
            VALUES(#{projectId}, #{docType}, #{title}, #{sourceRef}, #{originObjectKey}, #{originEtag},
                   #{mimeType}, #{parseStatus}, #{indexStatus})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RagDocument document);

    @Update("""
            UPDATE rag_documents
            SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND id = #{docId} AND deleted_at IS NULL
            """)
    int softDelete(@Param("projectId") Long projectId, @Param("docId") Long docId);

    @Update("""
            UPDATE rag_documents
            SET parse_status = #{parseStatus}, index_status = #{indexStatus}
            WHERE project_id = #{projectId} AND id = #{docId} AND deleted_at IS NULL
            """)
    int updateStatuses(@Param("projectId") Long projectId,
                       @Param("docId") Long docId,
                       @Param("parseStatus") String parseStatus,
                       @Param("indexStatus") String indexStatus);
}

