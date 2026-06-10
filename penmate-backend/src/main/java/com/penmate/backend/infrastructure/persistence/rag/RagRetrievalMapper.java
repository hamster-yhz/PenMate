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

    @Select("""
            SELECT rc.document_id, d.title AS document_title, rc.chunk_no, rc.content_text
            FROM rag_chunks rc
            JOIN rag_documents d ON d.document_id = rc.document_id
            WHERE rc.project_id = #{projectId}
              AND d.deleted_at IS NULL
              AND (
                    #{query} IS NULL
                    OR #{query} = ''
                    OR rc.content_text LIKE CONCAT('%', #{query}, '%')
                  )
              AND (
                    #{chapterId} IS NULL
                    OR d.title LIKE CONCAT('%chapter::', #{chapterId}, '::%')
                    OR rc.content_text LIKE CONCAT('%chapter=', #{chapterId}, '%')
                    OR d.title LIKE 'story_bible::%'
                  )
              AND (
                    #{storyBibleVersion} IS NULL
                    OR rc.content_text LIKE CONCAT('%matchedVersion=', #{storyBibleVersion}, '%')
                    OR d.title LIKE 'story_bible::%'
                  )
              AND (
                    #{entityHint} IS NULL
                    OR #{entityHint} = ''
                    OR rc.content_text REGEXP #{entityHint}
                    OR d.title REGEXP #{entityHint}
                  )
              AND (
                    #{searchScope} IS NULL
                    OR #{searchScope} <> 'AGENT_CONTEXT'
                    OR d.title LIKE 'story_bible::%'
                    OR d.title LIKE 'chapter::%'
                    OR rc.content_text LIKE '%sourceType=story_bible%'
                    OR rc.content_text LIKE '%sourceType=chapter%'
                  )
              AND (
                    #{activatedSkills} IS NULL
                    OR #{activatedSkills} = ''
                    OR #{activatedSkills} NOT LIKE '%story_bible_query%'
                    OR d.title LIKE 'story_bible::%'
                    OR d.title LIKE 'chapter::%'
                    OR rc.content_text LIKE '%canon=%'
                    OR rc.content_text LIKE '%entity=%'
                  )
              AND (
                    #{intentTags} IS NULL
                    OR #{intentTags} = ''
                    OR #{intentTags} NOT LIKE '%CONTINUITY_CHECK%'
                    OR rc.content_text LIKE '%matchedVersion=%'
                    OR rc.content_text LIKE '%chapter=%'
                    OR d.title LIKE 'chapter::%'
                  )
            ORDER BY rc.id DESC
            LIMIT #{limit}
            """)
    List<RagRetrievedChunk> searchChunksWithFilters(@Param("projectId") Long projectId,
                                                    @Param("query") String query,
                                                    @Param("limit") int limit,
                                                    @Param("chapterId") Long chapterId,
                                                    @Param("storyBibleVersion") Integer storyBibleVersion,
                                                    @Param("entityHint") String entityHint,
                                                    @Param("activatedSkills") String activatedSkills,
                                                    @Param("intentTags") String intentTags,
                                                    @Param("searchScope") String searchScope);

    @Insert("""
            INSERT INTO rag_retrieval_logs(retrieval_log_id, project_id, run_id, query_text, hit_count, sources_json, latency_ms, adopted, trace_id)
            VALUES(#{retrievalLogId}, #{projectId}, #{runId}, #{queryText}, #{hitCount}, #{sourcesJson}, #{latencyMs}, #{adopted}, #{traceId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRetrievalLog(RagRetrievalLog retrievalLog);

    @Select("""
            SELECT id, retrieval_log_id, project_id, run_id AS runId, query_text, hit_count,
                   CAST(sources_json AS CHAR) AS sources_json,
                   latency_ms, adopted, trace_id, created_at
            FROM rag_retrieval_logs
            WHERE project_id = #{projectId}
            ORDER BY id DESC
            LIMIT 100
            """)
    List<RagRetrievalLog> listRetrievalLogs(@Param("projectId") Long projectId);
}

