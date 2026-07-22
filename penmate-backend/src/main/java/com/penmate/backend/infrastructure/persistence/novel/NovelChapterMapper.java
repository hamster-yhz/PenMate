package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelChapter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * NovelChapterMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface NovelChapterMapper {

    @Select("""
            SELECT c.id, c.chapter_id, c.project_id, c.volume_id, c.title, c.sort_order,
                   c.word_count, c.content, c.content_revision,
                   c.lease_owner_type, c.lease_owner_id, c.lease_token, c.lease_expires_at,
                   c.created_at, c.updated_at, c.deleted_at
            FROM novel_chapters c
            LEFT JOIN novel_volumes v
              ON v.volume_id = c.volume_id AND v.project_id = c.project_id AND v.deleted_at IS NULL
            WHERE c.project_id = #{projectId} AND c.deleted_at IS NULL
            ORDER BY CASE WHEN c.volume_id IS NULL THEN 1 ELSE 0 END ASC,
                     COALESCE(v.sort_order, 2147483647) ASC,
                     c.sort_order ASC,
                     c.id ASC
            """)
    List<NovelChapter> findByProjectId(@Param("projectId") Long projectId);

    @Select("""
            SELECT id, chapter_id, project_id, volume_id, title, sort_order, word_count,
                   content, content_revision, lease_owner_type, lease_owner_id, lease_token, lease_expires_at,
                   created_at, updated_at, deleted_at
            FROM novel_chapters
            WHERE chapter_id = #{chapterId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    NovelChapter findByIdAndProjectId(@Param("projectId") Long projectId, @Param("chapterId") Long chapterId);

    @Insert("""
            INSERT INTO novel_chapters(
                chapter_id, project_id, volume_id, title, sort_order, word_count, content
            ) VALUES (
                #{chapterId}, #{projectId}, #{volumeId}, #{title}, #{sortOrder}, #{wordCount}, #{content}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NovelChapter chapter);

    @Update("""
            UPDATE novel_chapters
            SET volume_id = #{volumeId},
                title = #{title},
                sort_order = #{sortOrder}
            WHERE chapter_id = #{chapterId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int update(NovelChapter chapter);

    @Update("""
            UPDATE novel_chapters
            SET lease_owner_type = #{ownerType}, lease_owner_id = #{ownerId},
                lease_token = #{leaseToken}, lease_expires_at = #{expiresAt}
            WHERE chapter_id = #{chapterId} AND project_id = #{projectId} AND deleted_at IS NULL
              AND (lease_token IS NULL OR lease_expires_at <= CURRENT_TIMESTAMP(3) OR #{force} = TRUE)
            """)
    int acquireLease(@Param("projectId") Long projectId, @Param("chapterId") Long chapterId,
                     @Param("ownerType") String ownerType, @Param("ownerId") Long ownerId,
                     @Param("leaseToken") String leaseToken, @Param("expiresAt") java.time.Instant expiresAt,
                     @Param("force") boolean force);

    @Update("""
            UPDATE novel_chapters
            SET lease_owner_type = 'AI', lease_owner_id = #{runId},
                lease_token = #{leaseToken}, lease_expires_at = #{expiresAt}
            WHERE chapter_id = #{chapterId} AND project_id = #{projectId} AND deleted_at IS NULL
              AND (lease_token IS NULL OR lease_expires_at <= CURRENT_TIMESTAMP(3))
            """)
    int acquireAiLease(@Param("projectId") Long projectId, @Param("chapterId") Long chapterId,
                       @Param("actorUserId") Long actorUserId, @Param("runId") Long runId,
                       @Param("leaseToken") String leaseToken, @Param("expiresAt") java.time.Instant expiresAt);

    @Update("""
            UPDATE novel_chapters
            SET lease_expires_at = #{expiresAt}
            WHERE chapter_id = #{chapterId} AND project_id = #{projectId} AND lease_token = #{leaseToken}
              AND deleted_at IS NULL AND lease_expires_at > CURRENT_TIMESTAMP(3)
            """)
    int renewLease(@Param("projectId") Long projectId, @Param("chapterId") Long chapterId,
                   @Param("leaseToken") String leaseToken, @Param("expiresAt") java.time.Instant expiresAt);

    @Update("""
            UPDATE novel_chapters
            SET lease_owner_type = NULL, lease_owner_id = NULL, lease_token = NULL, lease_expires_at = NULL
            WHERE chapter_id = #{chapterId} AND project_id = #{projectId} AND lease_token = #{leaseToken}
              AND deleted_at IS NULL
            """)
    int releaseLease(@Param("projectId") Long projectId, @Param("chapterId") Long chapterId,
                     @Param("leaseToken") String leaseToken);

    @Update("""
            UPDATE novel_chapters
            SET content = #{content}, word_count = #{wordCount}, content_revision = content_revision + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE chapter_id = #{chapterId} AND project_id = #{projectId} AND deleted_at IS NULL
              AND lease_token = #{leaseToken} AND lease_expires_at > CURRENT_TIMESTAMP(3)
              AND content_revision = #{expectedRevision}
            """)
    int updateContent(@Param("projectId") Long projectId, @Param("chapterId") Long chapterId,
                      @Param("leaseToken") String leaseToken, @Param("expectedRevision") Long expectedRevision,
                      @Param("content") String content, @Param("wordCount") Integer wordCount);

    @Update("""
            UPDATE novel_chapters
            SET content = #{restoredContent}, word_count = #{wordCount}, content_revision = content_revision + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE chapter_id = #{chapterId} AND project_id = #{projectId} AND deleted_at IS NULL
              AND content_revision = #{expectedRevision} AND content = #{expectedContent}
              AND (lease_owner_type IS NULL OR lease_owner_type <> 'AI' OR lease_expires_at <= CURRENT_TIMESTAMP(3))
            """)
    int restoreAiContent(@Param("projectId") Long projectId, @Param("chapterId") Long chapterId,
                         @Param("expectedRevision") Long expectedRevision,
                         @Param("expectedContent") String expectedContent,
                         @Param("restoredContent") String restoredContent,
                         @Param("wordCount") Integer wordCount);

    @Update("""
            UPDATE novel_chapters
            SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE chapter_id = #{chapterId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int softDelete(@Param("projectId") Long projectId, @Param("chapterId") Long chapterId);

    @Update("""
            UPDATE novel_chapters
            SET deleted_at = CURRENT_TIMESTAMP(3),
                lease_owner_type = NULL, lease_owner_id = NULL, lease_token = NULL, lease_expires_at = NULL
            WHERE project_id = #{projectId} AND volume_id = #{volumeId} AND deleted_at IS NULL
            """)
    int softDeleteByVolume(@Param("projectId") Long projectId, @Param("volumeId") Long volumeId);

}
