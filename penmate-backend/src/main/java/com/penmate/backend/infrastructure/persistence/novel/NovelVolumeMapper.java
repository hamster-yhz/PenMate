package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelVolume;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NovelVolumeMapper {

    @Select("""
            SELECT id, project_id, title, sort_order, description, created_at, updated_at, deleted_at
            FROM novel_volumes
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            ORDER BY sort_order ASC, id ASC
            """)
    List<NovelVolume> findByProjectId(@Param("projectId") Long projectId);

    @Insert("""
            INSERT INTO novel_volumes(project_id, title, sort_order, description)
            VALUES(#{projectId}, #{title}, #{sortOrder}, #{description})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NovelVolume volume);

    @Update("""
            UPDATE novel_volumes
            SET title = #{title}, sort_order = #{sortOrder}, description = #{description}
            WHERE id = #{id} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int update(NovelVolume volume);

    @Update("""
            UPDATE novel_volumes
            SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{volumeId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int softDelete(@Param("projectId") Long projectId, @Param("volumeId") Long volumeId);
}

