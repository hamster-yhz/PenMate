package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.ChapterAiUndoOperation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface ChapterAiUndoMapper {

    String COLUMNS = "id, operation_id, project_id, chapter_id, run_id, tool_call_id, before_content, "
            + "before_word_count, result_content_hash, sequence_no, applied_revision, status, "
            + "created_at, updated_at, expires_at, undone_at";

    @Select("""
            SELECT id, operation_id, project_id, chapter_id, run_id, tool_call_id, before_content,
                   before_word_count, result_content_hash, sequence_no, applied_revision, status,
                   created_at, updated_at, expires_at, undone_at
            FROM novel_chapter_ai_undo_operations
            WHERE project_id = #{projectId} AND run_id = #{runId} AND chapter_id = #{chapterId}
              AND status = 'AVAILABLE' AND expires_at > CURRENT_TIMESTAMP(3)
            ORDER BY sequence_no DESC
            LIMIT 1
            FOR UPDATE
            """)
    ChapterAiUndoOperation findAvailableByRunAndChapter(@Param("projectId") Long projectId,
                                                         @Param("runId") Long runId,
                                                         @Param("chapterId") Long chapterId);

    @Select("""
            SELECT id, operation_id, project_id, chapter_id, run_id, tool_call_id, before_content,
                   before_word_count, result_content_hash, sequence_no, applied_revision, status,
                   created_at, updated_at, expires_at, undone_at
            FROM novel_chapter_ai_undo_operations
            WHERE project_id = #{projectId} AND operation_id = #{operationId}
            """)
    ChapterAiUndoOperation findByOperationId(@Param("projectId") Long projectId,
                                              @Param("operationId") Long operationId);

    @Select("""
            SELECT id, operation_id, project_id, chapter_id, run_id, tool_call_id, before_content,
                   before_word_count, result_content_hash, sequence_no, applied_revision, status,
                   created_at, updated_at, expires_at, undone_at
            FROM novel_chapter_ai_undo_operations
            WHERE project_id = #{projectId} AND chapter_id = #{chapterId}
              AND status = 'AVAILABLE' AND expires_at > CURRENT_TIMESTAMP(3)
            ORDER BY sequence_no DESC
            """)
    List<ChapterAiUndoOperation> listAvailableByChapter(@Param("projectId") Long projectId,
                                                        @Param("chapterId") Long chapterId);

    @Select("""
            SELECT id, operation_id, project_id, chapter_id, run_id, tool_call_id, before_content,
                   before_word_count, result_content_hash, sequence_no, applied_revision, status,
                   created_at, updated_at, expires_at, undone_at
            FROM novel_chapter_ai_undo_operations
            WHERE project_id = #{projectId} AND run_id = #{runId}
              AND status = 'AVAILABLE' AND expires_at > CURRENT_TIMESTAMP(3)
            ORDER BY chapter_id ASC, sequence_no DESC
            """)
    List<ChapterAiUndoOperation> listAvailableByRun(@Param("projectId") Long projectId,
                                                    @Param("runId") Long runId);

    @Select("""
            SELECT COALESCE(MAX(sequence_no), 0) + 1
            FROM novel_chapter_ai_undo_operations
            WHERE project_id = #{projectId} AND chapter_id = #{chapterId}
            """)
    long nextSequence(@Param("projectId") Long projectId, @Param("chapterId") Long chapterId);

    @Insert("""
            INSERT INTO novel_chapter_ai_undo_operations(
                operation_id, project_id, chapter_id, run_id, tool_call_id, before_content,
                before_word_count, result_content_hash, sequence_no, applied_revision, status, expires_at
            ) VALUES (
                #{operationId}, #{projectId}, #{chapterId}, #{runId}, #{toolCallId}, #{beforeContent},
                #{beforeWordCount}, #{resultContentHash}, #{sequenceNo}, #{appliedRevision}, #{status}, #{expiresAt}
            )
            """)
    int insert(ChapterAiUndoOperation operation);

    @Update("""
            UPDATE novel_chapter_ai_undo_operations
            SET tool_call_id = #{toolCallId}, result_content_hash = #{resultContentHash},
                applied_revision = #{appliedRevision}, expires_at = #{expiresAt},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId} AND status = 'AVAILABLE'
            """)
    int updateMergedResult(ChapterAiUndoOperation operation);

    @Update("""
            UPDATE novel_chapter_ai_undo_operations
            SET status = 'INVALIDATED', updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND chapter_id = #{chapterId} AND status = 'AVAILABLE'
            """)
    int invalidateAvailableByChapter(@Param("projectId") Long projectId, @Param("chapterId") Long chapterId);

    @Update("""
            UPDATE novel_chapter_ai_undo_operations
            SET status = 'UNDONE', undone_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId} AND status = 'AVAILABLE' AND expires_at > CURRENT_TIMESTAMP(3)
            """)
    int markUndone(@Param("operationId") Long operationId);

    @Update("""
            UPDATE novel_chapter_ai_undo_operations
            SET applied_revision = #{appliedRevision}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId} AND status = 'AVAILABLE'
            """)
    int rebaseAppliedRevision(@Param("operationId") Long operationId,
                              @Param("appliedRevision") Long appliedRevision);

    @Delete("""
            DELETE FROM novel_chapter_ai_undo_operations
            WHERE expires_at <= #{cutoff}
            """)
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
