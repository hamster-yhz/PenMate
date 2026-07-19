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
            JOIN rag_documents d ON d.document_id = rc.document_id
            WHERE rc.project_id = #{projectId}
              AND d.deleted_at IS NULL
              AND (
                    #{query,jdbcType=VARCHAR} IS NULL
                    OR #{query,jdbcType=VARCHAR} = ''
                    OR rc.content_text LIKE CONCAT('%', #{query,jdbcType=VARCHAR}, '%')
                  )
            ORDER BY rc.id DESC
            LIMIT #{limit}
            """)
    List<RagRetrievedChunk> searchChunks(@Param("projectId") Long projectId,
                                         @Param("query") String query,
                                         @Param("limit") int limit);

    @Insert("""
            INSERT INTO rag_retrieval_logs(retrieval_log_id, project_id, run_id, query_text, hit_count, sources_json, latency_ms, adopted, trace_id)
            VALUES(#{retrievalLogId}, #{projectId}, #{runId}, #{queryText}, #{hitCount}, #{sourcesJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler}, #{latencyMs}, #{adopted}, #{traceId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRetrievalLog(RagRetrievalLog retrievalLog);

    @Select("""
            SELECT id, retrieval_log_id, project_id, run_id AS runId, query_text, hit_count,
                   CAST(sources_json AS TEXT) AS sources_json,
                   latency_ms, adopted, trace_id, created_at
            FROM rag_retrieval_logs
            WHERE project_id = #{projectId}
            ORDER BY id DESC
            LIMIT 100
            """)
    List<RagRetrievalLog> listRetrievalLogs(@Param("projectId") Long projectId);
}

