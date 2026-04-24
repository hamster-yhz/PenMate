package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelCardRelation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * NovelCardRelationMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface NovelCardRelationMapper {

    @Select("""
            SELECT id, card_relation_id, project_id, from_card_id, to_card_id, relation_type, description,
                   created_at, deleted_at
            FROM novel_card_relations
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            ORDER BY id DESC
            """)
    List<NovelCardRelation> findByProjectId(@Param("projectId") Long projectId);

    @Insert("""
            INSERT INTO novel_card_relations(card_relation_id, project_id, from_card_id, to_card_id, relation_type, description)
            VALUES (#{cardRelationId}, #{projectId}, #{fromCardId}, #{toCardId}, #{relationType}, #{description})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NovelCardRelation relation);

    @Update("""
            UPDATE novel_card_relations
            SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE card_relation_id = #{relationId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int softDelete(@Param("projectId") Long projectId, @Param("relationId") Long relationId);
}

