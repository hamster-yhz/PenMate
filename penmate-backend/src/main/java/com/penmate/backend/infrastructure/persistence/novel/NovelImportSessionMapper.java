package com.penmate.backend.infrastructure.persistence.novel;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface NovelImportSessionMapper {
    @Insert("""
            INSERT INTO novel_import_sessions(
                session_id, owner_user_id, source_format, original_filename, draft_json,
                status, checkpoint_chapter, total_chapters
            ) VALUES (
                #{sessionId}, #{ownerUserId}, #{sourceFormat}, #{originalFilename},
                #{draftJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler},
                #{status}, 0, #{totalChapters}
            )
            """)
    int insert(Row row);

    @Select("SELECT " + COLUMNS + " FROM novel_import_sessions WHERE session_id = #{sessionId}")
    Row findById(@Param("sessionId") Long sessionId);

    @Select("SELECT " + COLUMNS + " FROM novel_import_sessions WHERE session_id = #{sessionId} AND owner_user_id = #{ownerUserId}")
    Row findByIdAndOwner(@Param("sessionId") Long sessionId, @Param("ownerUserId") Long ownerUserId);

    @Select("SELECT " + COLUMNS + " FROM novel_import_sessions WHERE session_id = #{sessionId} FOR UPDATE")
    Row lockById(@Param("sessionId") Long sessionId);

    @Update("""
            UPDATE novel_import_sessions
            SET draft_json = #{draftJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler},
                total_chapters = #{totalChapters}, status = 'READY', error_message = NULL,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE session_id = #{sessionId} AND owner_user_id = #{ownerUserId} AND status = 'DRAFT'
            """)
    int confirm(@Param("sessionId") Long sessionId, @Param("ownerUserId") Long ownerUserId,
                @Param("draftJson") String draftJson, @Param("totalChapters") int totalChapters);

    @Update("""
            UPDATE novel_import_sessions SET job_id = #{jobId}, status = 'QUEUED', updated_at = CURRENT_TIMESTAMP(3)
            WHERE session_id = #{sessionId} AND owner_user_id = #{ownerUserId} AND status = 'READY'
            """)
    int attachJob(@Param("sessionId") Long sessionId, @Param("ownerUserId") Long ownerUserId,
                  @Param("jobId") Long jobId);

    @Update("""
            UPDATE novel_import_sessions SET project_id = #{projectId}, status = 'IMPORTING', updated_at = CURRENT_TIMESTAMP(3)
            WHERE session_id = #{sessionId} AND project_id IS NULL AND status IN ('QUEUED', 'IMPORTING')
            """)
    int markPrepared(@Param("sessionId") Long sessionId, @Param("projectId") Long projectId);

    @Insert("""
            INSERT INTO novel_import_volume_map(session_id, volume_index, volume_id)
            VALUES (#{sessionId}, #{volumeIndex}, #{volumeId})
            ON CONFLICT (session_id, volume_index) DO NOTHING
            """)
    int insertVolumeMapping(@Param("sessionId") Long sessionId, @Param("volumeIndex") int volumeIndex,
                            @Param("volumeId") Long volumeId);

    @Select("SELECT volume_id FROM novel_import_volume_map WHERE session_id = #{sessionId} ORDER BY volume_index")
    List<Long> findVolumeIds(@Param("sessionId") Long sessionId);

    @Update("""
            UPDATE novel_import_sessions SET checkpoint_chapter = #{nextCheckpoint}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE session_id = #{sessionId} AND checkpoint_chapter = #{expectedCheckpoint} AND status = 'IMPORTING'
            """)
    int advanceCheckpoint(@Param("sessionId") Long sessionId, @Param("expectedCheckpoint") int expectedCheckpoint,
                          @Param("nextCheckpoint") int nextCheckpoint);

    @Update("UPDATE novel_import_sessions SET status = 'COMPLETED', updated_at = CURRENT_TIMESTAMP(3) WHERE session_id = #{sessionId} AND status = 'IMPORTING'")
    int markCompleted(@Param("sessionId") Long sessionId);

    @Update("UPDATE novel_import_sessions SET status = 'PAUSED', updated_at = CURRENT_TIMESTAMP(3) WHERE session_id = #{sessionId} AND owner_user_id = #{ownerUserId} AND status IN ('QUEUED', 'IMPORTING')")
    int markPaused(@Param("sessionId") Long sessionId, @Param("ownerUserId") Long ownerUserId);

    @Update("UPDATE novel_import_sessions SET status = 'IMPORTING', updated_at = CURRENT_TIMESTAMP(3) WHERE session_id = #{sessionId} AND owner_user_id = #{ownerUserId} AND status = 'PAUSED'")
    int resume(@Param("sessionId") Long sessionId, @Param("ownerUserId") Long ownerUserId);

    @Update("""
            UPDATE novel_import_sessions
            SET status = 'QUEUED', project_id = NULL, checkpoint_chapter = 0,
                error_message = NULL, updated_at = CURRENT_TIMESTAMP(3)
            WHERE session_id = #{sessionId} AND owner_user_id = #{ownerUserId} AND status IN ('FAILED', 'CANCELLED')
            """)
    int resetForRetry(@Param("sessionId") Long sessionId, @Param("ownerUserId") Long ownerUserId);

    @Delete("DELETE FROM novel_import_volume_map WHERE session_id = #{sessionId}")
    int deleteVolumeMappings(@Param("sessionId") Long sessionId);

    @Update("UPDATE novel_import_sessions SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP(3) WHERE session_id = #{sessionId} AND status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    int markCancelled(@Param("sessionId") Long sessionId);

    @Update("UPDATE novel_import_sessions SET status = 'FAILED', error_message = #{message}, updated_at = CURRENT_TIMESTAMP(3) WHERE session_id = #{sessionId} AND status <> 'COMPLETED'")
    int markFailed(@Param("sessionId") Long sessionId, @Param("message") String message);

    @Delete("DELETE FROM novel_chapters WHERE project_id = #{projectId}")
    int deleteProjectChapters(@Param("projectId") Long projectId);

    @Delete("DELETE FROM novel_volumes WHERE project_id = #{projectId}")
    int deleteProjectVolumes(@Param("projectId") Long projectId);

    @Delete("DELETE FROM novel_projects WHERE project_id = #{projectId} AND status = 0")
    int deleteHiddenProject(@Param("projectId") Long projectId);

    String COLUMNS = """
            session_id, owner_user_id, source_format, original_filename,
            CAST(draft_json AS TEXT) AS draft_json, status, project_id, job_id,
            checkpoint_chapter, total_chapters, error_message, created_at, updated_at
            """;

    class Row {
        public Long sessionId;
        public Long ownerUserId;
        public String sourceFormat;
        public String originalFilename;
        public String draftJson;
        public String status;
        public Long projectId;
        public Long jobId;
        public Integer checkpointChapter;
        public Integer totalChapters;
        public String errorMessage;
        public Instant createdAt;
        public Instant updatedAt;
    }
}
