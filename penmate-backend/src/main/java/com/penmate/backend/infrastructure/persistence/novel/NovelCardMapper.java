package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelCard;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * NovelCardMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface NovelCardMapper {

    @Select("""
            SELECT id, card_id, project_id, card_type, name, summary, detail_json,
                   created_at, updated_at, deleted_at
            FROM novel_cards
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            ORDER BY id DESC
            """)
    List<NovelCard> findByProjectId(@Param("projectId") Long projectId);

    @Select("""
            SELECT id, card_id, project_id, card_type, name, summary, detail_json,
                   created_at, updated_at, deleted_at
            FROM novel_cards
            WHERE card_id = #{cardId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    NovelCard findByIdAndProjectId(@Param("projectId") Long projectId, @Param("cardId") Long cardId);

    @Insert("""
            INSERT INTO novel_cards(card_id, project_id, card_type, name, summary, detail_json)
            VALUES (#{cardId}, #{projectId}, #{cardType}, #{name}, #{summary}, #{detailJson})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NovelCard card);

    @Update("""
            UPDATE novel_cards
            SET card_type = #{cardType},
                name = #{name},
                summary = #{summary},
                detail_json = #{detailJson}
            WHERE card_id = #{cardId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int update(NovelCard card);

    @Update("""
            UPDATE novel_cards
            SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE card_id = #{cardId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int softDelete(@Param("projectId") Long projectId, @Param("cardId") Long cardId);
}

