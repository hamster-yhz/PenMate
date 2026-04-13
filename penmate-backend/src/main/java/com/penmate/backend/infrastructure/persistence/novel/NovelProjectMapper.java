package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelProject;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NovelProjectMapper {

    @Select("""
            SELECT id, owner_user_id, title, summary, status, created_at, updated_at, deleted_at
            FROM novel_projects
            WHERE deleted_at IS NULL
            ORDER BY updated_at DESC
            """)
    List<NovelProject> findAll();

    @Select("""
            SELECT id, owner_user_id, title, summary, status, created_at, updated_at, deleted_at
            FROM novel_projects
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    NovelProject findById(@Param("id") Long id);

    @Insert("""
            INSERT INTO novel_projects(owner_user_id, title, summary, status)
            VALUES(#{ownerUserId}, #{title}, #{summary}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NovelProject project);

    @Update("""
            UPDATE novel_projects
            SET title = #{title}, summary = #{summary}, status = #{status}
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int update(NovelProject project);

    @Update("""
            UPDATE novel_projects
            SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int softDelete(@Param("id") Long id);
}

