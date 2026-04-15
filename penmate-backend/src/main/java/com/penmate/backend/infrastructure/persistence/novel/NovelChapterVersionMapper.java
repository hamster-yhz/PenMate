package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelChapterVersion;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * NovelChapterVersionMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface NovelChapterVersionMapper {

    @Select("""
            SELECT id, chapter_id, version_no, change_type, change_reason,
                   snapshot_object_key, snapshot_etag, snapshot_size, snapshot_checksum,
                   created_by, created_at
            FROM novel_chapter_versions
            WHERE chapter_id = #{chapterId}
            ORDER BY version_no DESC
            """)
    List<NovelChapterVersion> findByChapterId(@Param("chapterId") Long chapterId);

    @Select("""
            SELECT id, chapter_id, version_no, change_type, change_reason,
                   snapshot_object_key, snapshot_etag, snapshot_size, snapshot_checksum,
                   created_by, created_at
            FROM novel_chapter_versions
            WHERE chapter_id = #{chapterId} AND version_no = #{versionNo}
            """)
    NovelChapterVersion findByChapterAndVersion(@Param("chapterId") Long chapterId, @Param("versionNo") Integer versionNo);

    @Select("""
            SELECT COALESCE(MAX(version_no), 0)
            FROM novel_chapter_versions
            WHERE chapter_id = #{chapterId}
            """)
    Integer maxVersionNo(@Param("chapterId") Long chapterId);

    @Insert("""
            INSERT INTO novel_chapter_versions(
                chapter_id, version_no, change_type, change_reason,
                snapshot_object_key, snapshot_etag, snapshot_size, snapshot_checksum, created_by
            ) VALUES (
                #{chapterId}, #{versionNo}, #{changeType}, #{changeReason},
                #{snapshotObjectKey}, #{snapshotEtag}, #{snapshotSize}, #{snapshotChecksum}, #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NovelChapterVersion version);
}

