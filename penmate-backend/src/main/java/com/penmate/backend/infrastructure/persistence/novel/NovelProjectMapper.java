package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelProject;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * NovelProjectMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface NovelProjectMapper {

    @Select("""
            SELECT id, project_id, owner_user_id, title, summary, status, structure_revision,
                   created_at, updated_at, deleted_at
            FROM novel_projects
            WHERE deleted_at IS NULL
            ORDER BY updated_at DESC
            """)
    List<NovelProject> findAll();

    @Select("""
            SELECT id, project_id, owner_user_id, title, summary, status, structure_revision,
                   created_at, updated_at, deleted_at
            FROM novel_projects
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            """)
    NovelProject findByProjectId(@Param("projectId") Long projectId);

    @Insert("""
            INSERT INTO novel_projects(project_id, owner_user_id, title, summary, status, structure_revision)
            VALUES(#{projectId}, #{ownerUserId}, #{title}, #{summary}, #{status}, #{structureRevision})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NovelProject project);

    @Update("""
            UPDATE novel_projects
            SET title = #{title}, summary = #{summary}, status = #{status}
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            """)
    int update(NovelProject project);

    @Update("""
            UPDATE novel_projects
            SET structure_revision = structure_revision + 1
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            """)
    int incrementStructureRevision(@Param("projectId") Long projectId);

    @Update("""
            UPDATE novel_projects
            SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            """)
    int softDelete(@Param("projectId") Long projectId);
}

