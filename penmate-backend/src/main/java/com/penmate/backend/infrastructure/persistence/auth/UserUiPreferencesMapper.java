package com.penmate.backend.infrastructure.persistence.auth;

import com.penmate.backend.domain.auth.model.UserUiPreferences;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserUiPreferencesMapper {

    @Select("""
            SELECT id, user_id, theme_mode, editor_font_family, editor_font_size,
                   editor_line_height, editor_paragraph_spacing, editor_content_width,
                   typewriter_mode, highlight_current_paragraph, created_at, updated_at
            FROM user_ui_preferences
            WHERE user_id = #{userId}
            """)
    UserUiPreferences findByUserId(Long userId);

    @Insert("""
            INSERT INTO user_ui_preferences(
                user_id, theme_mode, editor_font_family, editor_font_size,
                editor_line_height, editor_paragraph_spacing, editor_content_width,
                typewriter_mode, highlight_current_paragraph
            ) VALUES (
                #{userId}, #{themeMode}, #{editorFontFamily}, #{editorFontSize},
                #{editorLineHeight}, #{editorParagraphSpacing}, #{editorContentWidth},
                #{typewriterMode}, #{highlightCurrentParagraph}
            )
            ON CONFLICT (user_id) DO UPDATE SET
                theme_mode = EXCLUDED.theme_mode,
                editor_font_family = EXCLUDED.editor_font_family,
                editor_font_size = EXCLUDED.editor_font_size,
                editor_line_height = EXCLUDED.editor_line_height,
                editor_paragraph_spacing = EXCLUDED.editor_paragraph_spacing,
                editor_content_width = EXCLUDED.editor_content_width,
                typewriter_mode = EXCLUDED.typewriter_mode,
                highlight_current_paragraph = EXCLUDED.highlight_current_paragraph,
                updated_at = CURRENT_TIMESTAMP(3)
            """)
    int upsert(UserUiPreferences preferences);
}
