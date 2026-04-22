package com.penmate.backend.infrastructure.persistence.rag;

import com.penmate.backend.domain.rag.model.RagRetrievalLog;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RagRetrievalMapper {

    @Select("""
            SELECT rc.document_id, d.title AS document_title, rc.chunk_no, rc.content_text
            FROM rag_chunks rc
            JOIN rag_documents d ON d.id = rc.document_id
            WHERE rc.project_id = #{projectId}
              AND d.deleted_at IS NULL
              AND (
                    #{query} IS NULL
                    OR #{query} = ''
                    OR rc.content_text LIKE CONCAT('%', #{query}, '%')
                  )
            ORDER BY rc.id DESC
            LIMIT #{limit}
            """)
    List<RagRetrievedChunk> searchChunks(@Param("projectId") Long projectId,
                                         @Param("query") String query,
                                         @Param("limit") int limit);

    @Insert("""
            INSERT INTO rag_retrieval_logs(project_id, task_id, query_text, hit_count, sources_json, latency_ms, adopted, trace_id)
            VALUES(#{projectId}, #{taskId}, #{queryText}, #{hitCount}, #{sourcesJson}, #{latencyMs}, #{adopted}, #{traceId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRetrievalLog(RagRetrievalLog retrievalLog);

    @Select("""
            SELECT id, project_id, task_id, query_text, hit_count,
                   CAST(sources_json AS CHAR) AS sources_json,
                   latency_ms, adopted, trace_id, created_at
            FROM rag_retrieval_logs
            WHERE project_id = #{projectId}
            ORDER BY id DESC
            LIMIT 100
            """)
    List<RagRetrievalLog> listRetrievalLogs(@Param("projectId") Long projectId);
}

