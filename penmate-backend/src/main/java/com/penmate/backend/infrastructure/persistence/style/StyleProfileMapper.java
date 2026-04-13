package com.penmate.backend.infrastructure.persistence.style;

import com.penmate.backend.domain.style.model.StyleProfile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface StyleProfileMapper {

    @Select("""
            SELECT id, project_id, name, is_default, pace, tone, narrative_focus,
                   prompt_template, sample_text, created_at, updated_at, deleted_at
            FROM style_profiles
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            ORDER BY is_default DESC, id DESC
            """)
    List<StyleProfile> findByProjectId(@Param("projectId") Long projectId);

    @Select("""
            SELECT id, project_id, name, is_default, pace, tone, narrative_focus,
                   prompt_template, sample_text, created_at, updated_at, deleted_at
            FROM style_profiles
            WHERE project_id = #{projectId} AND id = #{styleId} AND deleted_at IS NULL
            """)
    StyleProfile findById(@Param("projectId") Long projectId, @Param("styleId") Long styleId);

    @Select("""
            SELECT id, project_id, name, is_default, pace, tone, narrative_focus,
                   prompt_template, sample_text, created_at, updated_at, deleted_at
            FROM style_profiles
            WHERE project_id = #{projectId} AND is_default = 1 AND deleted_at IS NULL
            LIMIT 1
            """)
    StyleProfile findDefaultByProjectId(@Param("projectId") Long projectId);

    @Insert("""
            INSERT INTO style_profiles(project_id, name, is_default, pace, tone, narrative_focus, prompt_template, sample_text)
            VALUES (#{projectId}, #{name}, #{isDefault}, #{pace}, #{tone}, #{narrativeFocus}, #{promptTemplate}, #{sampleText})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(StyleProfile styleProfile);

    @Update("""
            UPDATE style_profiles
            SET name = #{name},
                pace = #{pace},
                tone = #{tone},
                narrative_focus = #{narrativeFocus},
                prompt_template = #{promptTemplate},
                sample_text = #{sampleText}
            WHERE project_id = #{projectId} AND id = #{id} AND deleted_at IS NULL
            """)
    int update(StyleProfile styleProfile);

    @Update("""
            UPDATE style_profiles
            SET deleted_at = CURRENT_TIMESTAMP(3),
                is_default = 0
            WHERE project_id = #{projectId} AND id = #{styleId} AND deleted_at IS NULL
            """)
    int softDelete(@Param("projectId") Long projectId, @Param("styleId") Long styleId);

    @Update("""
            UPDATE style_profiles
            SET is_default = 0
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            """)
    int clearDefaultByProjectId(@Param("projectId") Long projectId);

    @Update("""
            UPDATE style_profiles
            SET is_default = 1
            WHERE project_id = #{projectId} AND id = #{styleId} AND deleted_at IS NULL
            """)
    int setDefault(@Param("projectId") Long projectId, @Param("styleId") Long styleId);

    @Insert("""
            INSERT INTO style_switch_logs(project_id, from_style_id, to_style_id, switched_by, warning_confirmed, reason)
            VALUES (#{projectId}, #{fromStyleId}, #{toStyleId}, #{switchedBy}, #{warningConfirmed}, #{reason})
            """)
    int insertSwitchLog(@Param("projectId") Long projectId,
                        @Param("fromStyleId") Long fromStyleId,
                        @Param("toStyleId") Long toStyleId,
                        @Param("switchedBy") Long switchedBy,
                        @Param("warningConfirmed") boolean warningConfirmed,
                        @Param("reason") String reason);
}

