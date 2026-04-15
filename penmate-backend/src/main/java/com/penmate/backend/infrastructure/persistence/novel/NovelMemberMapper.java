package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelMember;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * NovelMemberMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface NovelMemberMapper {

    @Select("""
            SELECT project_id, user_id, member_role, joined_at
            FROM novel_members
            WHERE project_id = #{projectId}
            ORDER BY joined_at DESC
            """)
    List<NovelMember> findByProjectId(@Param("projectId") Long projectId);

    @Insert("""
            INSERT INTO novel_members(project_id, user_id, member_role)
            VALUES(#{projectId}, #{userId}, #{memberRole})
            """)
    int insert(NovelMember member);

    @Update("""
            UPDATE novel_members
            SET member_role = #{memberRole}
            WHERE project_id = #{projectId} AND user_id = #{userId}
            """)
    int updateRole(@Param("projectId") Long projectId, @Param("userId") Long userId, @Param("memberRole") String memberRole);

    @Update("""
            DELETE FROM novel_members
            WHERE project_id = #{projectId} AND user_id = #{userId}
            """)
    int delete(@Param("projectId") Long projectId, @Param("userId") Long userId);
}

