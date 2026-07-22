package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelCoverUploadSession;
import com.penmate.backend.domain.novel.model.NovelProject;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.Instant;
import java.util.List;

@Mapper
public interface NovelCoverUploadMapper {
    @Insert("""
            INSERT INTO novel_cover_upload_sessions(
                upload_id, project_id, owner_user_id, operation_type, original_filename,
                declared_mime_type, expected_size, expected_checksum, original_object_key,
                upload_token_hash, status, expires_at)
            VALUES(#{uploadId}, #{projectId}, #{ownerUserId}, #{operationType}, #{originalFilename},
                   #{declaredMimeType}, #{expectedSize}, #{expectedChecksum}, #{originalObjectKey},
                   #{uploadTokenHash}, #{status}, #{expiresAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NovelCoverUploadSession session);

    @Select("""
            SELECT * FROM novel_cover_upload_sessions WHERE upload_id = #{uploadId}
            """)
    NovelCoverUploadSession findById(@Param("uploadId") Long uploadId);

    @Select("""
            SELECT * FROM novel_cover_upload_sessions WHERE upload_id = #{uploadId} FOR UPDATE
            """)
    NovelCoverUploadSession findByIdForUpdate(@Param("uploadId") Long uploadId);

    @Select("""
            SELECT * FROM novel_cover_upload_sessions
            WHERE project_id = #{projectId}
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    NovelCoverUploadSession findLatestByProject(@Param("projectId") Long projectId);

    @Select("""
            SELECT * FROM novel_cover_upload_sessions
            WHERE project_id = #{projectId} AND status = 'COMPLETED'
            ORDER BY updated_at DESC, id DESC
            LIMIT 1
            """)
    NovelCoverUploadSession findLatestCompletedByProject(@Param("projectId") Long projectId);

    @Select("""
            SELECT * FROM novel_cover_upload_sessions
            WHERE status = 'PENDING' AND expires_at < #{now}
            ORDER BY expires_at
            LIMIT 100
            """)
    List<NovelCoverUploadSession> findExpiredPending(@Param("now") Instant now);

    @Select("""
            SELECT * FROM novel_projects
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            FOR UPDATE
            """)
    NovelProject lockProject(@Param("projectId") Long projectId);

    @Update("""
            UPDATE novel_projects
            SET cover_pending_upload_id = #{uploadId}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND owner_user_id = #{ownerUserId} AND deleted_at IS NULL
            """)
    int setPendingUpload(@Param("projectId") Long projectId,
                         @Param("ownerUserId") Long ownerUserId,
                         @Param("uploadId") Long uploadId);

    @Update("""
            UPDATE novel_cover_upload_sessions
            SET crop_x = #{cropX}, crop_y = #{cropY}, crop_width = #{cropWidth}, crop_height = #{cropHeight},
                image_width = #{imageWidth}, image_height = #{imageHeight},
                display_object_key = #{displayObjectKey}, thumbnail_object_key = #{thumbnailObjectKey},
                status = 'PROCESSING', error_code = NULL, error_message = NULL,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE upload_id = #{uploadId} AND status = 'PENDING'
            """)
    int markProcessing(@Param("uploadId") Long uploadId,
                       @Param("cropX") Double cropX, @Param("cropY") Double cropY,
                       @Param("cropWidth") Double cropWidth, @Param("cropHeight") Double cropHeight,
                       @Param("imageWidth") Integer imageWidth, @Param("imageHeight") Integer imageHeight,
                       @Param("displayObjectKey") String displayObjectKey,
                       @Param("thumbnailObjectKey") String thumbnailObjectKey);

    @Update("""
            UPDATE novel_cover_upload_sessions
            SET status = 'PROCESSING', error_code = NULL, error_message = NULL,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE upload_id = #{uploadId} AND status = 'FAILED'
            """)
    int retry(@Param("uploadId") Long uploadId);

    @Update("""
            UPDATE novel_cover_upload_sessions
            SET status = 'COMPLETED', updated_at = CURRENT_TIMESTAMP(3)
            WHERE upload_id = #{uploadId} AND status = 'PROCESSING'
            """)
    int markCompleted(@Param("uploadId") Long uploadId);

    @Update("""
            UPDATE novel_cover_upload_sessions
            SET status = 'FAILED', error_code = #{errorCode}, error_message = #{errorMessage},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE upload_id = #{uploadId} AND status = 'PROCESSING'
            """)
    int markFailed(@Param("uploadId") Long uploadId,
                   @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE novel_cover_upload_sessions
            SET status = 'SUPERSEDED', updated_at = CURRENT_TIMESTAMP(3)
            WHERE upload_id = #{uploadId} AND status IN ('PENDING', 'PROCESSING', 'FAILED')
            """)
    int markSuperseded(@Param("uploadId") Long uploadId);

    @Update("""
            UPDATE novel_cover_upload_sessions
            SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP(3)
            WHERE upload_id = #{uploadId} AND status = 'PENDING'
            """)
    int markExpired(@Param("uploadId") Long uploadId);

    @Update("""
            UPDATE novel_projects
            SET cover_original_object_key = #{originalObjectKey},
                cover_display_object_key = #{displayObjectKey},
                cover_thumbnail_object_key = #{thumbnailObjectKey},
                cover_pending_upload_id = NULL,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND cover_pending_upload_id = #{uploadId} AND deleted_at IS NULL
            """)
    int applyCover(@Param("projectId") Long projectId, @Param("uploadId") Long uploadId,
                   @Param("originalObjectKey") String originalObjectKey,
                   @Param("displayObjectKey") String displayObjectKey,
                   @Param("thumbnailObjectKey") String thumbnailObjectKey);

    @Update("""
            UPDATE novel_projects
            SET cover_original_object_key = NULL, cover_display_object_key = NULL,
                cover_thumbnail_object_key = NULL, cover_pending_upload_id = NULL,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND owner_user_id = #{ownerUserId} AND deleted_at IS NULL
            """)
    int clearCover(@Param("projectId") Long projectId, @Param("ownerUserId") Long ownerUserId);
}
