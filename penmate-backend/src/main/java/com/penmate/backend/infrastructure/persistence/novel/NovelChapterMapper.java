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
            SELECT id, chapter_id, project_id, volume_id, outline_node_id, title, chapter_no, status, word_count,
                   excerpt, content_object_key, content_etag, content_size, content_checksum,
                   storage_provider, last_generated_at, created_at, updated_at, deleted_at
            FROM novel_chapters
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            ORDER BY chapter_no ASC, id ASC
            """)
    List<NovelChapter> findByProjectId(@Param("projectId") Long projectId);

    @Select("""
            SELECT id, chapter_id, project_id, volume_id, outline_node_id, title, chapter_no, status, word_count,
                   excerpt, content_object_key, content_etag, content_size, content_checksum,
                   storage_provider, last_generated_at, created_at, updated_at, deleted_at
            FROM novel_chapters
            WHERE chapter_id = #{chapterId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    NovelChapter findByIdAndProjectId(@Param("projectId") Long projectId, @Param("chapterId") Long chapterId);

    @Insert("""
            INSERT INTO novel_chapters(
                chapter_id, project_id, volume_id, outline_node_id, title, chapter_no, status, word_count, excerpt,
                content_object_key, content_etag, content_size, content_checksum, storage_provider
            ) VALUES (
                #{chapterId}, #{projectId}, #{volumeId}, #{outlineNodeId}, #{title}, #{chapterNo}, #{status}, #{wordCount}, #{excerpt},
                #{contentObjectKey}, #{contentEtag}, #{contentSize}, #{contentChecksum}, #{storageProvider}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NovelChapter chapter);

    @Update("""
            UPDATE novel_chapters
            SET volume_id = #{volumeId},
                outline_node_id = #{outlineNodeId},
                title = #{title},
                chapter_no = #{chapterNo},
                status = #{status},
                word_count = #{wordCount},
                excerpt = #{excerpt},
                content_object_key = #{contentObjectKey},
                content_etag = #{contentEtag},
                content_size = #{contentSize},
                content_checksum = #{contentChecksum},
                storage_provider = #{storageProvider}
            WHERE chapter_id = #{chapterId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int update(NovelChapter chapter);

    @Update("""
            UPDATE novel_chapters
            SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE chapter_id = #{chapterId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int softDelete(@Param("projectId") Long projectId, @Param("chapterId") Long chapterId);

    @Update("""
            UPDATE novel_chapters
            SET status = 2, last_generated_at = CURRENT_TIMESTAMP(3)
            WHERE chapter_id = #{chapterId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int publish(@Param("projectId") Long projectId, @Param("chapterId") Long chapterId);

    @Update("""
            UPDATE novel_chapters
            SET content_object_key = #{objectKey},
                content_etag = #{etag},
                content_size = #{size},
                content_checksum = #{checksum},
                storage_provider = #{storageProvider}
            WHERE chapter_id = #{chapterId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int updateContentMeta(@Param("projectId") Long projectId,
                          @Param("chapterId") Long chapterId,
                          @Param("objectKey") String objectKey,
                          @Param("etag") String etag,
                          @Param("size") Long size,
                          @Param("checksum") String checksum,
                          @Param("storageProvider") String storageProvider);
}
