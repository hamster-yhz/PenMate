package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelOutlineNode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * NovelOutlineNodeMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface NovelOutlineNodeMapper {

    @Select("""
            SELECT id, project_id, parent_id, title, node_type, sort_order, content,
                   created_at, updated_at, deleted_at
            FROM novel_outline_nodes
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            ORDER BY parent_id ASC, sort_order ASC, id ASC
            """)
    List<NovelOutlineNode> findByProjectId(@Param("projectId") Long projectId);

    @Select("""
            SELECT id, project_id, parent_id, title, node_type, sort_order, content,
                   created_at, updated_at, deleted_at
            FROM novel_outline_nodes
            WHERE id = #{nodeId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    NovelOutlineNode findByIdAndProjectId(@Param("projectId") Long projectId, @Param("nodeId") Long nodeId);

    @Insert("""
            INSERT INTO novel_outline_nodes(project_id, parent_id, title, node_type, sort_order, content)
            VALUES (#{projectId}, #{parentId}, #{title}, #{nodeType}, #{sortOrder}, #{content})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NovelOutlineNode node);

    @Update("""
            UPDATE novel_outline_nodes
            SET parent_id = #{parentId},
                title = #{title},
                node_type = #{nodeType},
                sort_order = #{sortOrder},
                content = #{content}
            WHERE id = #{id} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int update(NovelOutlineNode node);

    @Update("""
            UPDATE novel_outline_nodes
            SET parent_id = #{parentId},
                sort_order = #{sortOrder}
            WHERE id = #{nodeId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int move(@Param("projectId") Long projectId,
             @Param("nodeId") Long nodeId,
             @Param("parentId") Long parentId,
             @Param("sortOrder") Integer sortOrder);

    @Update("""
            UPDATE novel_outline_nodes
            SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{nodeId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int softDelete(@Param("projectId") Long projectId, @Param("nodeId") Long nodeId);
}

